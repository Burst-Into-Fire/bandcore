package com.burstintofire.bandcore.tenancy.invitation;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.audit.AuditRecorder;
import com.burstintofire.bandcore.identity.Accounts;
import com.burstintofire.bandcore.shared.config.PlatformProperties;
import com.burstintofire.bandcore.shared.id.IdGenerator;
import com.burstintofire.bandcore.shared.persistence.RowLevelSecurity;
import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;
import com.burstintofire.bandcore.tenancy.Role;
import com.burstintofire.bandcore.tenancy.band.Band;
import com.burstintofire.bandcore.tenancy.band.BandRegistry;
import com.burstintofire.bandcore.tenancy.member.MemberEnrollment;
import com.burstintofire.bandcore.tenancy.membership.Membership;
import com.burstintofire.bandcore.tenancy.membership.MembershipAccess;
import com.burstintofire.bandcore.tenancy.membership.MembershipRepository;

/**
 * The invitee's side, on the central host: the band is not known from the host, so it comes from
 * the token, and the transaction's RLS context is switched to it.
 */
@Service
public class InvitationAcceptance {

	/** What the invitation page shows before the invitee logs in or signs up. */
	public record Preview(String bandName, String bandSlug, String email, Role role) {
	}

	/** Where the new member goes next. */
	public record Accepted(String bandSlug, String bandUrl, Role role) {
	}

	private final InvitationTokens tokens;

	private final InvitationRepository invitations;

	private final MembershipRepository memberships;

	private final MembershipAccess access;

	private final MemberEnrollment enrollment;

	private final BandRegistry bands;

	private final Accounts accounts;

	private final RowLevelSecurity rowLevelSecurity;

	private final AuditRecorder audit;

	private final PlatformProperties platform;

	private final IdGenerator ids;

	private final Clock clock;

	InvitationAcceptance(InvitationTokens tokens, InvitationRepository invitations, MembershipRepository memberships,
			MembershipAccess access, MemberEnrollment enrollment, BandRegistry bands, Accounts accounts,
			RowLevelSecurity rowLevelSecurity, AuditRecorder audit, PlatformProperties platform, IdGenerator ids,
			Clock clock) {
		this.tokens = tokens;
		this.invitations = invitations;
		this.memberships = memberships;
		this.access = access;
		this.enrollment = enrollment;
		this.bands = bands;
		this.accounts = accounts;
		this.rowLevelSecurity = rowLevelSecurity;
		this.audit = audit;
		this.platform = platform;
		this.ids = ids;
		this.clock = clock;
	}

	/** Unknown, used, revoked and expired tokens all answer 400 TOKEN_INVALID. */
	@Transactional
	public Preview preview(String token) {
		Invitation invitation = pendingInvitation(token, null);
		Band band = activeBand(invitation.getBandId());
		return new Preview(band.getName(), band.getSlug(), invitation.getEmail(), invitation.getRole());
	}

	/**
	 * Gives the logged-in account access to the band. The account's verified email must be the
	 * invited one. A removed membership is reactivated with the new role.
	 */
	@Transactional
	public Accepted accept(String token) {
		UUID userId = CurrentUser.require().userId();
		Accounts.Account account = accounts.find(userId)
			.filter(Accounts.Account::active)
			.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Access denied."));
		Invitation invitation = pendingInvitation(token, userId);
		Band band = activeBand(invitation.getBandId());
		if (!account.emailVerified()) {
			throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.EMAIL_NOT_VERIFIED,
					"Confirm your email before accepting the invitation.");
		}
		if (!account.email().equals(invitation.getEmail())) {
			throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.INVITATION_EMAIL_MISMATCH,
					"This invitation was sent to another email. Log in with the invited email.");
		}
		Instant now = clock.instant();
		Membership membership = memberships.findByBandIdAndUserId(band.getId(), userId).orElse(null);
		if (membership != null && membership.isActive()) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ALREADY_A_MEMBER, "You already have access to this band.");
		}
		if (membership == null) {
			membership = memberships.saveAndFlush(new Membership(ids.newId(), band.getId(), userId,
					invitation.getRole(), now));
			audit.record("membership.created", "membership", membership.getId(),
					Map.of("user_id", userId.toString(), "role", invitation.getRole().name()));
		}
		else {
			membership.reactivate(invitation.getRole(), now);
			memberships.flush();
			audit.record("membership.reactivated", "membership", membership.getId(),
					Map.of("user_id", userId.toString(), "role", invitation.getRole().name()));
		}
		// Read-only managers get access without becoming a member (integrante) of the band.
		enrollment.enroll(band.getId(), userId, account.displayName(), invitation.getMemberId(),
				invitation.getRole() != Role.READ_ONLY);
		invitation.accept(userId, now);
		tokens.delete(invitation.getId());
		audit.record("invitation.accepted", "invitation", invitation.getId(), Map.of("user_id", userId.toString()));
		access.evict(band.getId(), userId);
		return new Accepted(band.getSlug(), platform.bandUrl(band.getSlug()), invitation.getRole());
	}

	/** Looks the token up, switches the transaction to its band and locks the invitation. */
	private Invitation pendingInvitation(String token, UUID userId) {
		Instant now = clock.instant();
		InvitationTokens.Lookup lookup = tokens.find(token, now).orElseThrow(InvitationAcceptance::invalidToken);
		rowLevelSecurity.switchBand(lookup.bandId(), userId);
		return invitations.findByBandIdAndId(lookup.bandId(), lookup.invitationId())
			.filter(invitation -> invitation.state(now) == Invitation.State.PENDING)
			.orElseThrow(InvitationAcceptance::invalidToken);
	}

	private Band activeBand(UUID bandId) {
		return bands.find(bandId).filter(Band::isActive).orElseThrow(InvitationAcceptance::invalidToken);
	}

	private static ApiException invalidToken() {
		return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.TOKEN_INVALID,
				"The invitation is invalid or expired. Ask for a new one.");
	}

}
