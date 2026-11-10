package com.burstintofire.bandcore.merch.product;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.burstintofire.bandcore.shared.persistence.TenantRepository;

/** Every query filters by band (defense in depth on top of RLS). */
public interface ProductVariantRepository extends TenantRepository<ProductVariant, UUID> {

	Optional<ProductVariant> findByBandIdAndId(UUID bandId, UUID id);

	@Query("SELECT v FROM ProductVariant v WHERE v.bandId = :bandId AND v.id IN :ids")
	List<ProductVariant> findByBandIdAndIdIn(UUID bandId, List<UUID> ids);

	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("SELECT v FROM ProductVariant v WHERE v.bandId = :bandId AND v.id IN :ids ORDER BY v.id")
	List<ProductVariant> findForSaleByBandIdAndIdIn(@Param("bandId") UUID bandId, @Param("ids") List<UUID> ids);

	@Query("SELECT v FROM ProductVariant v WHERE v.bandId = :bandId ORDER BY v.productId, v.position, v.id")
	List<ProductVariant> findByBandId(UUID bandId);

	@Query("""
			SELECT v FROM ProductVariant v WHERE v.bandId = :bandId AND v.productId = :productId
			ORDER BY v.position, v.id
			""")
	List<ProductVariant> findByBandIdAndProductId(UUID bandId, UUID productId);

	@Query("""
			SELECT coalesce(max(v.position), -1) + 1 FROM ProductVariant v
			WHERE v.bandId = :bandId AND v.productId = :productId
			""")
	int nextPosition(UUID bandId, UUID productId);

	/** @param sizeKey and colorKey: lowercase, empty when not set (the unique index's expressions) */
	@Query("""
			SELECT count(v) > 0 FROM ProductVariant v
			WHERE v.bandId = :bandId AND v.productId = :productId AND v.id <> :exceptId
			  AND lower(coalesce(v.size, '')) = :sizeKey AND lower(coalesce(v.color, '')) = :colorKey
			""")
	boolean attributesTaken(UUID bandId, UUID productId, String sizeKey, String colorKey, UUID exceptId);

	@Query("""
			SELECT count(v) > 0 FROM ProductVariant v
			WHERE v.bandId = :bandId AND lower(v.sku) = lower(:sku) AND v.id <> :exceptId
			""")
	boolean skuTaken(UUID bandId, String sku, UUID exceptId);

}
