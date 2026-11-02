package com.burstintofire.bandcore.tenancy.membership;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.audit.AuditRecorder;
import com.burstintofire.bandcore.identity.BandSessions;
import com.burstintofire.bandcore.shared.security.CurrentUser;
import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;
import com.burstintofire.bandcore.tenancy.Role;
import com.burstintofire.bandcore.tenancy.band.BandRegistry;

/**
 * Who has access to the current band, and with which role. Every change takes effect on the
 * next request: the access cache is evicted, and removing access also revokes the user's
 * refresh tokens for this band. A band never ends up without an active admin: changes lock the
 * band row, so two admins demoting each other at the same time cannot both succeed.
 */
@Service
public class MembershipAdministration {

	private final MembershipRepository memberships;

	private final MembershipAccess access;

	private final BandRegistry bands;

	private final BandSessions sessions;

	private final AuditRecorder audit;

	private final Clock clock;

	MembershipAdministration(MembershipRepository memberships, MembershipAccess access, BandRegistry bands,
			BandSessions sessions, AuditRecorder audit, Clock clock) {
		this.memberships = memberships;
		this.access = access;
		this.bands = bands;
		this.sessions = sessions;
		this.audit = audit;
		this.clock = clock;
	}

	/** Active memberships of the current band. */
	@PreAuthorize(Permissions.CAN_MANAGE_MEMBERS)
	@Transactional(readOnly = true)
	public List<Membership> list() {
		return memberships.findByBandIdAndStatus(bandId(), MembershipStatus.ACTIVE);
	}

	@PreAuthorize(Permissions.CAN_MANAGE_MEMBERS)
	@Transactional
	public Membership changeRole(UUID membershipId, Role role) {
		UUID bandId = lockBand();
		Membership membership = findActive(bandId, membershipId);
		Role previous = membership.getRole();
		if (previous == role) {
			return membership;
		}
		if (previous == Role.ADMIN) {
			requireAnotherAdmin(bandId);
		}
		membership.changeRole(role, clock.instant());
		memberships.flush();
		access.evict(bandId, membership.getUserId());
		audit.record("membership.role_changed", "membership", membership.getId(),
				Map.of("role", AuditRecorder.change(previous.name(), role.name())));
		return membership;
	}

	/** Removes someone else's access; they are logged out of this band at once. */
	@PreAuthorize(Permissions.CAN_MANAGE_MEMBERS)
	@Transactional
	public void remove(UUID membershipId) {
		UUID bandId = lockBand();
		Membership membership = findActive(bandId, membershipId);
		end(bandId, membership, "membership.removed");
	}

	/** Any role may leave the band, except its last admin. */
	@PreAuthorize(Permissions.CAN_READ_BAND)
	@Transactional
	public void leave() {
		UUID bandId = lockBand();
		UUID userId = CurrentUser.require().userId();
		Membership membership = memberships.findByBandIdAndUserId(bandId, userId)
			.filter(Membership::isActive)
			.orElseThrow(ApiException::notFound);
		end(bandId, membership, "membership.left");
	}

	private void end(UUID bandId, Membership membership, String action) {
		if (membership.getRole() == Role.ADMIN) {
			requireAnotherAdmin(bandId);
		}
		Instant now = clock.instant();
		membership.remove(now);
		memberships.flush();
		access.evict(bandId, membership.getUserId());
		sessions.endSessionsInBand(membership.getUserId(), bandId);
		audit.record(action, "membership", membership.getId(),
				Map.of("user_id", membership.getUserId().toString(), "role", membership.getRole().name()));
	}

	/** Called with the band locked, so the count cannot change before the transaction ends. */
	private void requireAnotherAdmin(UUID bandId) {
		if (memberships.countByBandIdAndRoleAndStatus(bandId, Role.ADMIN, MembershipStatus.ACTIVE) <= 1) {
			throw new ApiException(HttpStatus.CONFLICT, ErrorCode.LAST_ADMIN,
					"The band must keep at least one admin. Make someone else an admin first.");
		}
	}

	private Membership findActive(UUID bandId, UUID membershipId) {
		return memberships.findByBandIdAndId(bandId, membershipId)
			.filter(Membership::isActive)
			.orElseThrow(ApiException::notFound);
	}

	private UUID lockBand() {
		UUID bandId = TenantContextHolder.require().bandId();
		bands.lock(bandId);
		return bandId;
	}

	private static UUID bandId() {
		return TenantContextHolder.require().bandId();
	}

}
