package com.burstintofire.bandcore.merch.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.burstintofire.bandcore.shared.persistence.TenantRepository;

/** Every query filters by band (defense in depth on top of RLS). */
public interface EventRepository extends TenantRepository<Event, UUID> {

	Optional<Event> findByBandIdAndId(UUID bandId, UUID id);

	List<Event> findByBandIdOrderByStartsAtAscIdAsc(UUID bandId);

	List<Event> findByBandIdAndStartsAtGreaterThanEqualOrderByStartsAtAscIdAsc(UUID bandId, Instant startsAt);

	List<Event> findByBandIdAndStartsAtLessThanOrderByStartsAtDescIdDesc(UUID bandId, Instant startsAt);

}
