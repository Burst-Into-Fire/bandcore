package com.burstintofire.bandcore.finance.category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;

import com.burstintofire.bandcore.shared.persistence.TenantRepository;

/** Every query filters by band (defense in depth on top of RLS). */
public interface ExpenseCategoryRepository extends TenantRepository<ExpenseCategory, UUID> {

	Optional<ExpenseCategory> findByBandIdAndId(UUID bandId, UUID id);

	@Query("SELECT c FROM ExpenseCategory c WHERE c.bandId = :bandId ORDER BY lower(c.name)")
	List<ExpenseCategory> findByBandId(UUID bandId);

	@Query("""
			SELECT count(c) > 0 FROM ExpenseCategory c
			WHERE c.bandId = :bandId AND lower(c.name) = lower(:name) AND c.id <> :exceptId
			""")
	boolean nameTaken(UUID bandId, String name, UUID exceptId);

}
