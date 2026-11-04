package com.burstintofire.bandcore.merch.product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;

import com.burstintofire.bandcore.shared.persistence.TenantRepository;

/** Every query filters by band (defense in depth on top of RLS). */
public interface ProductRepository extends TenantRepository<Product, UUID> {

	Optional<Product> findByBandIdAndId(UUID bandId, UUID id);

	@Query("SELECT p FROM Product p WHERE p.bandId = :bandId ORDER BY lower(p.name), p.id")
	List<Product> findByBandId(UUID bandId);

	@Query("""
			SELECT count(p) > 0 FROM Product p
			WHERE p.bandId = :bandId AND lower(p.name) = lower(:name) AND p.id <> :exceptId
			""")
	boolean nameTaken(UUID bandId, String name, UUID exceptId);

}
