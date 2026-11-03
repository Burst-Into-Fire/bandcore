package com.burstintofire.bandcore.finance.expense;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.burstintofire.bandcore.shared.persistence.TenantRepository;

/** Every query filters by band (defense in depth on top of RLS). */
public interface ExpenseSplitRepository extends TenantRepository<ExpenseSplit, UUID> {

	/** The shares of a page of expenses at once, so lists do not query once per expense. */
	List<ExpenseSplit> findByBandIdAndExpenseIdIn(UUID bandId, Collection<UUID> expenseIds);

	@Modifying(flushAutomatically = true, clearAutomatically = false)
	@Query("DELETE FROM ExpenseSplit s WHERE s.bandId = :bandId AND s.expenseId = :expenseId")
	int deleteByExpense(UUID bandId, UUID expenseId);

}
