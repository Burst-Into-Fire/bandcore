package com.burstintofire.bandcore.tenancy.membership;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.identity.BandAccess;

/** Answers the identity module's band access questions from the memberships table. */
@Component
class MembershipAccess implements BandAccess {

	private final MembershipRepository memberships;

	MembershipAccess(MembershipRepository memberships) {
		this.memberships = memberships;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Grant> find(UUID bandId, UUID userId) {
		return memberships.findByBandIdAndUserId(bandId, userId)
			.filter(Membership::isActive)
			.map(membership -> new Grant(membership.getRole().name(), Set.of()));
	}

}
