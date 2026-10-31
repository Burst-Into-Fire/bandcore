package com.burstintofire.bandcore.shared.persistence;

import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Base for repositories of tenant tables. Unlike {@code JpaRepository}, it has no
 * {@code findById} or {@code findAll}: every query method must name the band explicitly
 * ({@code findByBandIdAndId}), as a second barrier on top of row-level security.
 */
@NoRepositoryBean
public interface TenantRepository<T, ID> extends Repository<T, ID> {

	<S extends T> S save(S entity);

	<S extends T> S saveAndFlush(S entity);

	void flush();

}
