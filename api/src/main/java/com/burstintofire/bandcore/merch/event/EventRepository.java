package com.burstintofire.bandcore.merch.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.burstintofire.bandcore.shared.persistence.TenantRepository;

/** Every query filters by band (defense in depth on top of RLS). */
public interface EventRepository extends TenantRepository<Event, UUID> {

	Optional<Event> findByBandIdAndId(UUID bandId, UUID id);

	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("SELECT e FROM Event e WHERE e.bandId = :bandId AND e.id = :id")
	Optional<Event> findForSaleByBandIdAndId(@Param("bandId") UUID bandId, @Param("id") UUID id);

	List<Event> findByBandIdOrderByStartsAtAscIdAsc(UUID bandId);

	List<Event> findByBandIdAndStartsAtGreaterThanEqualOrderByStartsAtAscIdAsc(UUID bandId, Instant startsAt);

	List<Event> findByBandIdAndStartsAtLessThanOrderByStartsAtDescIdDesc(UUID bandId, Instant startsAt);

}
