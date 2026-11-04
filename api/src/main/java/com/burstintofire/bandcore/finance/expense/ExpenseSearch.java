package com.burstintofire.bandcore.finance.expense;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.burstintofire.bandcore.shared.money.Currency;

/**
 * Filtered, sorted and paginated expense lists. Always filters by band and skips soft-deleted
 * expenses, like {@link ExpenseRepository}.
 */
@Component
class ExpenseSearch {

	enum Sort {

		DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC, CREATED_DESC

	}

	/**
	 * Null fields do not filter. {@code text} matches the description, ignoring case.
	 * {@code payerMemberId} filters by who paid.
	 */
	record Filter(YearMonth month, ExpenseStatus status, UUID categoryId, UUID payerMemberId, Currency currency,
			String text) {
	}

	private final EntityManager entityManager;

	ExpenseSearch(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	/** Deep enough for any band; the controller enforces the same limits. */
	static final int MAX_PAGE = 10_000;

	static final int MAX_SIZE = 100;

	Page<Expense> search(UUID bandId, Filter filter, Sort sort, int page, int size) {
		if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_SIZE) {
			throw new IllegalArgumentException("Page " + page + " of size " + size + " is out of range");
		}
		CriteriaBuilder cb = entityManager.getCriteriaBuilder();

		CriteriaQuery<Expense> query = cb.createQuery(Expense.class);
		Root<Expense> expense = query.from(Expense.class);
		query.select(expense).where(predicates(cb, expense, bandId, filter)).orderBy(order(cb, expense, sort));
		List<Expense> items = entityManager.createQuery(query)
			.setFirstResult(Math.multiplyExact(page, size))
			.setMaxResults(size)
			.getResultList();

		CriteriaQuery<Long> count = cb.createQuery(Long.class);
		Root<Expense> counted = count.from(Expense.class);
		count.select(cb.count(counted)).where(predicates(cb, counted, bandId, filter));
		long total = entityManager.createQuery(count).getSingleResult();

		return new PageImpl<>(items, PageRequest.of(page, size), total);
	}

	private static Predicate[] predicates(CriteriaBuilder cb, Root<Expense> expense, UUID bandId, Filter filter) {
		List<Predicate> predicates = new ArrayList<>();
		predicates.add(cb.equal(expense.get("bandId"), bandId));
		predicates.add(cb.isNull(expense.get("deletedAt")));
		if (filter.month() != null) {
			predicates.add(cb.between(expense.get("expenseDate"), filter.month().atDay(1), filter.month().atEndOfMonth()));
		}
		if (filter.status() != null) {
			predicates.add(cb.equal(expense.get("status"), filter.status()));
		}
		if (filter.categoryId() != null) {
			predicates.add(cb.equal(expense.get("categoryId"), filter.categoryId()));
		}
		if (filter.payerMemberId() != null) {
			predicates.add(cb.equal(expense.get("paidByMemberId"), filter.payerMemberId()));
		}
		if (filter.currency() != null) {
			predicates.add(cb.equal(expense.get("currency"), filter.currency()));
		}
		if (filter.text() != null && !filter.text().isBlank()) {
			String pattern = "%" + escapeLike(filter.text().strip().toLowerCase(Locale.ROOT)) + "%";
			predicates.add(cb.like(cb.lower(expense.get("description")), pattern, '\\'));
		}
		return predicates.toArray(Predicate[]::new);
	}

	/** The id breaks ties, so pages never overlap or skip rows. */
	private static List<Order> order(CriteriaBuilder cb, Root<Expense> expense, Sort sort) {
		return switch (sort) {
			case DATE_DESC -> List.of(cb.desc(expense.get("expenseDate")), cb.desc(expense.get("id")));
			case DATE_ASC -> List.of(cb.asc(expense.get("expenseDate")), cb.asc(expense.get("id")));
			case AMOUNT_DESC -> List.of(cb.desc(expense.get("amountCents")), cb.desc(expense.get("id")));
			case AMOUNT_ASC -> List.of(cb.asc(expense.get("amountCents")), cb.asc(expense.get("id")));
			case CREATED_DESC -> List.of(cb.desc(expense.get("createdAt")), cb.desc(expense.get("id")));
		};
	}

	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

}
