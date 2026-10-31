package com.burstintofire.bandcore.tenancy.band;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BandRepository extends JpaRepository<Band, UUID> {

	@Query("SELECT b FROM Band b WHERE lower(b.slug) = lower(:slug)")
	Optional<Band> findBySlug(String slug);

	@Query("SELECT count(b) > 0 FROM Band b WHERE lower(b.slug) = lower(:slug)")
	boolean existsBySlug(String slug);

	long countByCreatedBy(UUID createdBy);

	List<Band> findByIdIn(Collection<UUID> ids);

}
