package com.burstintofire.bandcore.merch.batch;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;

import com.burstintofire.bandcore.shared.persistence.TenantRepository;

/** Every query filters by band (defense in depth on top of RLS). */
public interface BatchItemRepository extends TenantRepository<BatchItem, UUID> {

	@Query("""
			SELECT i FROM BatchItem i WHERE i.bandId = :bandId AND i.batchId IN :batchIds
			ORDER BY i.batchId, i.position
			""")
	List<BatchItem> findByBandIdAndBatchIdIn(UUID bandId, List<UUID> batchIds);

}
