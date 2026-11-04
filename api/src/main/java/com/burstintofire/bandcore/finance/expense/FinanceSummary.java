package com.burstintofire.bandcore.finance.expense;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.burstintofire.bandcore.shared.money.Currency;
import com.burstintofire.bandcore.shared.security.Permissions;
import com.burstintofire.bandcore.tenancy.BandCalendar;

/**
 * The month's finance numbers for the dashboard (RF36), in one call: per currency, the total of
 * expenses dated in the month, how much is paid and how much is planned, and the same by
 * category; plus the month's latest expenses and the planned expenses coming up. Currencies are
 * never added together (RN08).
 */
@Service
public class FinanceSummary {

	static final int LATEST = 5;

	static final int UPCOMING_DAYS = 30;

	public record Totals(long totalCents, long paidCents, long plannedCents) {

		Totals plus(ExpenseStatus status, long cents) {
			return status == ExpenseStatus.PAID
					? new Totals(Math.addExact(totalCents, cents), Math.addExact(paidCents, cents), plannedCents)
					: new Totals(Math.addExact(totalCents, cents), paidCents, Math.addExact(plannedCents, cents));
		}

		static final Totals ZERO = new Totals(0, 0, 0);

	}

	public record CategoryTotals(UUID categoryId, Totals totals) {
	}

	public record CurrencySummary(Currency currency, Totals totals, List<CategoryTotals> categories) {
	}

	public record MonthSummary(YearMonth month, List<CurrencySummary> currencies,
			List<Expenses.ExpenseDetails> latest, List<Expenses.ExpenseDetails> upcoming) {
	}

	private final ExpenseRepository expenses;

	private final Expenses details;

	private final BandCalendar calendar;

	FinanceSummary(ExpenseRepository expenses, Expenses details, BandCalendar calendar) {
		this.expenses = expenses;
		this.details = details;
		this.calendar = calendar;
	}

	/** @param month null for the current month in the band's time zone */
	@PreAuthorize(Permissions.CAN_READ_FINANCE)
	@Transactional(readOnly = true)
	public MonthSummary of(YearMonth month) {
		YearMonth target = month != null ? month : YearMonth.from(calendar.today());
		List<ExpenseRepository.MonthTotal> rows = expenses.totalsBetween(Expenses.bandId(), target.atDay(1),
				target.atEndOfMonth());
		var latest = details
			.list(new ExpenseSearch.Filter(target, null, null, null, null, null), ExpenseSearch.Sort.DATE_DESC, 0,
					LATEST)
			.getContent();
		return new MonthSummary(target, summarize(rows), latest, details.upcoming(UPCOMING_DAYS));
	}

	/** Currencies in enum order; categories by total, largest first. */
	static List<CurrencySummary> summarize(List<ExpenseRepository.MonthTotal> rows) {
		Map<Currency, Map<UUID, Totals>> byCurrency = new EnumMap<>(Currency.class);
		for (ExpenseRepository.MonthTotal row : rows) {
			byCurrency.computeIfAbsent(row.currency(), currency -> new LinkedHashMap<>())
				.merge(row.categoryId(), Totals.ZERO.plus(row.status(), row.totalCents()),
						(a, b) -> new Totals(Math.addExact(a.totalCents(), b.totalCents()),
								Math.addExact(a.paidCents(), b.paidCents()),
								Math.addExact(a.plannedCents(), b.plannedCents())));
		}
		List<CurrencySummary> result = new ArrayList<>();
		byCurrency.forEach((currency, categories) -> {
			Totals sum = Totals.ZERO;
			for (Totals totals : categories.values()) {
				sum = new Totals(Math.addExact(sum.totalCents(), totals.totalCents()),
						Math.addExact(sum.paidCents(), totals.paidCents()),
						Math.addExact(sum.plannedCents(), totals.plannedCents()));
			}
			List<CategoryTotals> sorted = categories.entrySet()
				.stream()
				.map(entry -> new CategoryTotals(entry.getKey(), entry.getValue()))
				.sorted(Comparator.comparingLong((CategoryTotals totals) -> totals.totals().totalCents())
					.reversed()
					.thenComparing(totals -> totals.categoryId().toString()))
				.toList();
			result.add(new CurrencySummary(currency, sum, sorted));
		});
		return List.copyOf(result);
	}

}
