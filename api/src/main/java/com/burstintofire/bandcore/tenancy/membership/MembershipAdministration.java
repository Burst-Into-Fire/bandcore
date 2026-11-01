package com.burstintofire.bandcore.tenancy.membership;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.tenancy.Role;

/**
 * Changes memberships of the current band and evicts the access cache, so every change takes
 * effect on the next request. The endpoints, the last-admin rule and audit come in M4-03.
 */
@Service
public class MembershipAdministration {

	private final MembershipRepository memberships;

	private final MembershipAccess access;

	private final Clock clock;

	MembershipAdministration(MembershipRepository memberships, MembershipAccess access, Clock clock) {
		this.memberships = memberships;
		this.access = access;
		this.clock = clock;
	}

	@Transactional
	public void changeRole(UUID userId, Role role) {
		Membership membership = find(userId);
		membership.changeRole(role, clock.instant());
		access.evict(membership.getBandId(), userId);
	}

	@Transactional
	public void remove(UUID userId) {
		Membership membership = find(userId);
		membership.remove(clock.instant());
		access.evict(membership.getBandId(), userId);
	}

	private Membership find(UUID userId) {
		UUID bandId = TenantContextHolder.require().bandId();
		return memberships.findByBandIdAndUserId(bandId, userId).orElseThrow(ApiException::notFound);
	}

}
