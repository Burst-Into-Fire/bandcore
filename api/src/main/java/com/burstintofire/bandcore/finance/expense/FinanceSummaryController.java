package com.burstintofire.bandcore.finance.expense;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.finance.expense.ExpenseController.ExpenseResponse;
import com.burstintofire.bandcore.shared.money.Currency;

/** The dashboard's finance numbers. */
@RestController
@RequestMapping("/summary")
@Tag(name = "Summary")
class FinanceSummaryController {

	private final FinanceSummary summary;

	FinanceSummaryController(FinanceSummary summary) {
		this.summary = summary;
	}

	@GetMapping("/finance")
	@Operation(operationId = "getFinanceSummary", summary = "The month's expenses for the dashboard, per currency",
			description = "Expenses count in the month of their date: total, paid and planned, overall and by category "
					+ "(largest first). Also the month's latest expenses and the planned expenses of the next 30 "
					+ "days, overdue first. Without month, the current month in the band's time zone.")
	FinanceSummaryResponse get(
			@Parameter(description = "yyyy-MM", schema = @Schema(type = "string", example = "2026-11"))
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
		FinanceSummary.MonthSummary result = summary.of(month);
		return new FinanceSummaryResponse(result.month().toString(),
				result.currencies().stream().map(CurrencySummaryResponse::of).toList(),
				result.latest().stream().map(ExpenseResponse::of).toList(),
				result.upcoming().stream().map(ExpenseResponse::of).toList());
	}

	record FinanceSummaryResponse(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11") String month,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Only currencies with expenses in the month") List<CurrencySummaryResponse> currencies,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "The month's latest expenses by date (at most 5)") List<ExpenseResponse> latest,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Planned expenses of the next 30 days, overdue first") List<ExpenseResponse> upcoming) {
	}

	record CurrencySummaryResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Currency currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalCents,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long paidCents,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long plannedCents,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CategoryTotalsResponse> categories) {

		static CurrencySummaryResponse of(FinanceSummary.CurrencySummary summary) {
			return new CurrencySummaryResponse(summary.currency(), summary.totals().totalCents(),
					summary.totals().paidCents(), summary.totals().plannedCents(),
					summary.categories()
						.stream()
						.map(category -> new CategoryTotalsResponse(category.categoryId(), category.totals().totalCents(),
								category.totals().paidCents(), category.totals().plannedCents()))
						.toList());
		}

	}

	record CategoryTotalsResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID categoryId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalCents,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long paidCents,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long plannedCents) {
	}

}
