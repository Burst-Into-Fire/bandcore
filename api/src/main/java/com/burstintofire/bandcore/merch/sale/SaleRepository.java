package com.burstintofire.bandcore.merch.sale;

import java.util.Optional;
import java.util.UUID;

import com.burstintofire.bandcore.shared.persistence.TenantRepository;

/** Every query filters by band (defense in depth on top of RLS). */
public interface SaleRepository extends TenantRepository<Sale, UUID> {

	Optional<Sale> findByBandIdAndId(UUID bandId, UUID id);

}
