package com.burstintofire.bandcore.finance.expense;

import java.util.Optional;
import java.util.UUID;

import com.burstintofire.bandcore.shared.persistence.TenantRepository;

/**
 * Every query filters by band (defense in depth on top of RLS) and skips soft-deleted expenses.
 * Lists with filters go through {@link ExpenseSearch}.
 */
public interface ExpenseRepository extends TenantRepository<Expense, UUID> {

	Optional<Expense> findByBandIdAndIdAndDeletedAtIsNull(UUID bandId, UUID id);

}
