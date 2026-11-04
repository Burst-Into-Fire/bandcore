package com.burstintofire.bandcore.merch.batch;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import com.burstintofire.bandcore.shared.persistence.TenantRepository;

/** Every query filters by band (defense in depth on top of RLS). */
public interface ProductionBatchRepository extends TenantRepository<ProductionBatch, UUID> {

	Optional<ProductionBatch> findByBandIdAndId(UUID bandId, UUID id);

	@Query(value = """
			SELECT b FROM ProductionBatch b WHERE b.bandId = :bandId
			ORDER BY b.receivedOn DESC, b.createdAt DESC, b.id DESC
			""", countQuery = "SELECT count(b) FROM ProductionBatch b WHERE b.bandId = :bandId")
	Page<ProductionBatch> findByBandId(UUID bandId, Pageable pageable);

}
