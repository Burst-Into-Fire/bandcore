package com.burstintofire.bandcore.tenancy.invitation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.audit.AuditRecorder;
import com.burstintofire.bandcore.identity.Accounts;
import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.ratelimit.RateLimiter;
import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;
import com.burstintofire.bandcore.tenancy.Role;
import com.burstintofire.bandcore.tenancy.band.Band;
import com.burstintofire.bandcore.tenancy.band.BandRegistry;
import com.burstintofire.bandcore.tenancy.member.Member;
import com.burstintofire.bandcore.tenancy.member.MemberQueries;
import com.burstintofire.bandcore.tenancy.membership.Membership;
import com.burstintofire.bandcore.tenancy.membership.MembershipRepository;

/**
 * Admins invite people to the current band by email, with a role and optionally the member
 * record they will be linked to. The token is single use and valid for 7 days.
 */
@Service
public class InvitationService {

	static final Duration LIFETIME = Duration.ofDays(7);

	private final InvitationRepository invitations;

	private final InvitationTokens tokens;

	private final MembershipRepository memberships;

	private final MemberQueries members;

	private final BandRegistry bands;

	private final Accounts accounts;

	private final AuditRecorder audit;

	private final ApplicationEventPublisher events;

	private final RateLimiter.RateLimit invitationsPerBand;

	private final IdGenerator ids;

	private final Clock clock;

	InvitationService(InvitationRepository invitations, InvitationTokens tokens, MembershipRepository memberships,
			MemberQueries members, BandRegistry bands, Accounts accounts, AuditRecorder audit,
			ApplicationEventPublisher events, RateLimiter limiter, IdGenerator ids, Clock clock) {
		this.invitations = invitations;
		this.tokens = tokens;
		this.memberships = memberships;
		this.members = members;
		this.bands = bands;
		this.accounts = accounts;
		this.audit = audit;
		this.events = events;
		this.invitationsPerBand = limiter.limit("invitations-band");
		this.ids = ids;
		this.clock = clock;
	}

	@PreAuthorize(Permissions.CAN_MANAGE_MEMBERS)
	@Transactional(readOnly = true)
	public List<Invitation> list() {
		return invitations.findByBandIdOrderByCreatedAtDesc(bandId());
	}

	@PreAuthorize(Permissions.CAN_MANAGE_MEMBERS)
	@Transactional
	public Invitation invite(String rawEmail, Role role, UUID memberId) {
		UUID bandId = bandId();
		String email = Accounts.normalizeEmail(rawEmail);
		boolean alreadyMember = accounts.findByEmail(email)
			.flatMap(account -> memberships.findByBandIdAndUserId(bandId, account.id()))
			.filter(Membership::isActive)
			.isPresent();
		if (alreadyMember) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ALREADY_A_MEMBER,
					"This email already has access to the band.", "email");
		}
		if (memberId != null) {
			Member member = members.get(memberId);
			if (member.getUserId() != null) {
				throw new ApiException(HttpStatus.CONFLICT, ErrorCode.MEMBER_ALREADY_LINKED,
						"This member is already linked to an account.", "memberId");
			}
		}
		Instant now = clock.instant();
		var pending = invitations.findByBandIdAndEmailAndStatus(bandId, email, InvitationStatus.PENDING);
		if (pending.isPresent()) {
			if (pending.get().state(now) == Invitation.State.PENDING) {
				throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INVITATION_PENDING,
						"There is already a pending invitation for this email. Send it again instead.", "email");
			}
			// Expired: it gives way to the new invitation.
			pending.get().revoke(now);
			tokens.delete(pending.get().getId());
			invitations.flush();
		}
		invitationsPerBand.consume(bandId.toString());
		Invitation invitation = invitations.saveAndFlush(new Invitation(ids.newId(), bandId, email, role, memberId,
				CurrentUser.require().userId(), now.plus(LIFETIME), now));
		Map<String, Object> changes = new LinkedHashMap<>();
		changes.put("role", role.name());
		changes.put("member_id", memberId == null ? null : memberId.toString());
		audit.record("invitation.created", "invitation", invitation.getId(), changes);
		send(invitation);
		return invitation;
	}

	/** A new token and a new 7-day expiry; the previous link stops working. */
	@PreAuthorize(Permissions.CAN_MANAGE_MEMBERS)
	@Transactional
	public Invitation resend(UUID invitationId) {
		Invitation invitation = find(invitationId);
		if (!invitation.isPending()) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
					"Only pending or expired invitations can be sent again.");
		}
		invitationsPerBand.consume(invitation.getBandId().toString());
		Instant now = clock.instant();
		invitation.renew(now.plus(LIFETIME), now);
		invitations.flush();
		audit.record("invitation.resent", "invitation", invitation.getId(), Map.of());
		send(invitation);
		return invitation;
	}

	@PreAuthorize(Permissions.CAN_MANAGE_MEMBERS)
	@Transactional
	public void revoke(UUID invitationId) {
		Invitation invitation = find(invitationId);
		if (invitation.isPending()) {
			invitation.revoke(clock.instant());
			tokens.delete(invitation.getId());
			audit.record("invitation.revoked", "invitation", invitation.getId(), Map.of());
		}
	}

	private void send(Invitation invitation) {
		String token = tokens.issue(invitation);
		Band band = bands.find(invitation.getBandId()).orElseThrow();
		String inviter = accounts.find(invitation.getInvitedBy()).map(Accounts.Account::displayName).orElse("Alguém");
		events.publishEvent(new InvitationEmailRequested(invitation.getEmail(), band.getName(), inviter,
				invitation.getRole(), token));
	}

	private Invitation find(UUID invitationId) {
		return invitations.findByBandIdAndId(bandId(), invitationId).orElseThrow(ApiException::notFound);
	}

	private static UUID bandId() {
		return TenantContextHolder.require().bandId();
	}

}
