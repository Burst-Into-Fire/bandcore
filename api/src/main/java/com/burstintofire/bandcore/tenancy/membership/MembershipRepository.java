package com.burstintofire.bandcore.tenancy.membership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.burstintofire.bandcore.shared.persistence.TenantRepository;
import com.burstintofire.bandcore.tenancy.Role;

/**
 * Every query names the band or the user explicitly (defense in depth on top of RLS).
 */
public interface MembershipRepository extends TenantRepository<Membership, UUID> {

	Optional<Membership> findByBandIdAndUserId(UUID bandId, UUID userId);

	List<Membership> findByUserIdAndStatus(UUID userId, MembershipStatus status);

	Optional<Membership> findByBandIdAndId(UUID bandId, UUID id);

	List<Membership> findByBandIdAndStatus(UUID bandId, MembershipStatus status);

	long countByBandIdAndRoleAndStatus(UUID bandId, Role role, MembershipStatus status);

}
