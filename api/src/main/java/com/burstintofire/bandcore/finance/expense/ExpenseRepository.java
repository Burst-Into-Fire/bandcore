package com.burstintofire.bandcore.finance.expense;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;

import com.burstintofire.bandcore.shared.persistence.TenantRepository;

/**
 * Every query filters by band (defense in depth on top of RLS) and skips soft-deleted expenses.
 * Lists with filters go through {@link ExpenseSearch}.
 */
public interface ExpenseRepository extends TenantRepository<Expense, UUID> {

	Optional<Expense> findByBandIdAndIdAndDeletedAtIsNull(UUID bandId, UUID id);

	/** Planned expenses dated up to {@code until}, overdue ones included, oldest first. */
	@Query("""
			SELECT e FROM Expense e
			WHERE e.bandId = :bandId AND e.deletedAt IS NULL AND e.status = com.burstintofire.bandcore.finance.expense.ExpenseStatus.PLANNED AND e.expenseDate <= :until
			ORDER BY e.expenseDate, e.id
			""")
	List<Expense> findUpcoming(UUID bandId, LocalDate until, Limit limit);

	default List<Expense> findUpcoming(UUID bandId, LocalDate until) {
		return findUpcoming(bandId, until, Limit.of(100));
	}

}
