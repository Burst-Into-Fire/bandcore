package com.burstintofire.bandcore.tenancy.member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.burstintofire.bandcore.shared.persistence.TenantRepository;

/**
 * Every query filters by band (defense in depth on top of RLS). Never add a method that loads
 * members by id alone.
 */
public interface MemberRepository extends TenantRepository<Member, UUID> {

	Optional<Member> findByBandIdAndId(UUID bandId, UUID id);

	List<Member> findByBandIdOrderByDisplayNameAsc(UUID bandId);

}
