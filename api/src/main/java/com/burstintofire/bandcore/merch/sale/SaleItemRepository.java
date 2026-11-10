package com.burstintofire.bandcore.merch.sale;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.burstintofire.bandcore.shared.persistence.TenantRepository;

/** Every query filters by band (defense in depth on top of RLS). */
public interface SaleItemRepository extends TenantRepository<SaleItem, UUID> {

	@Query("SELECT i FROM SaleItem i WHERE i.bandId = :bandId AND i.saleId = :saleId ORDER BY i.position")
	List<SaleItem> findByBandIdAndSaleIdOrderByPosition(@Param("bandId") UUID bandId, @Param("saleId") UUID saleId);

}
