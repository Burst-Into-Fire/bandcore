package com.burstintofire.bandcore.finance.expense;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.burstintofire.bandcore.shared.money.Currency;
import com.burstintofire.bandcore.shared.web.PageResponse;

/** Expenses of the current band. Amounts are integer cents in the expense's currency. */
@RestController
@RequestMapping("/expenses")
@Tag(name = "Expenses")
class ExpenseController {

	static final int MAX_PAGE_SIZE = 100;

	private final Expenses expenses;

	ExpenseController(Expenses expenses) {
		this.expenses = expenses;
	}

	@GetMapping
	@Operation(operationId = "listExpenses", summary = "Expenses of the current band, filtered and paginated",
			description = "Deleted expenses are not listed. Pages start at 0.")
	PageResponse<ExpenseResponse> list(
			@Parameter(description = "yyyy-MM, by expense date", schema = @Schema(type = "string", example = "2026-11"))
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
			@RequestParam(required = false) ExpenseStatus status, @RequestParam(required = false) UUID categoryId,
			@Parameter(description = "The member who paid") @RequestParam(required = false) UUID payerMemberId,
			@RequestParam(required = false) Currency currency,
			@Parameter(description = "Text in the description") @RequestParam(required = false) @Size(
					max = 100) String q,
			@RequestParam(defaultValue = "DATE_DESC") ExpenseSearch.Sort sort,
			@RequestParam(defaultValue = "0") @PositiveOrZero @Max(10_000) int page,
			@RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
		var filter = new ExpenseSearch.Filter(month, status, categoryId, payerMemberId, currency, q);
		return PageResponse.of(expenses.list(filter, sort, page, size), ExpenseResponse::of);
	}

	@GetMapping("/{expenseId}")
	@Operation(operationId = "getExpense", summary = "One expense of the current band")
	ExpenseResponse get(@PathVariable UUID expenseId) {
		return ExpenseResponse.of(expenses.get(expenseId));
	}

	@PostMapping
	@Operation(operationId = "createExpense", summary = "Record a planned or paid expense",
			description = "422 PAYER_REQUIRED when a paid expense has no payer, MEMBER_INACTIVE when the payer left "
					+ "the band, CATEGORY_ARCHIVED for an archived category.")
	ResponseEntity<ExpenseResponse> create(@Valid @RequestBody CreateExpenseRequest request) {
		Expenses.ExpenseDetails created = expenses.create(new Expenses.NewExpense(request.description(),
				request.categoryId(), request.currency(), request.amountCents(), request.expenseDate(), request.status(),
				request.paidByMemberId(), request.paidOn(), request.notes(), SplitBody.toRequest(request.split())));
		return ResponseEntity.created(URI.create("/api/expenses/" + created.expense().getId()))
			.body(ExpenseResponse.of(created));
	}

	@PatchMapping("/{expenseId}")
	@Operation(operationId = "updateExpense", summary = "Change an expense (own ones; admins any)",
			description = "Send the version that was read: 409 VERSION_CONFLICT when someone else changed the expense "
					+ "meanwhile. Omitted fields are left unchanged; empty notes clear them.")
	ExpenseResponse update(@PathVariable UUID expenseId, @Valid @RequestBody UpdateExpenseRequest request) {
		return ExpenseResponse.of(expenses.update(expenseId,
				new Expenses.ExpenseChanges(request.version(), request.description(), request.categoryId(),
						request.currency(), request.amountCents(), request.expenseDate(), request.paidByMemberId(),
						request.paidOn(), request.notes(), SplitBody.toRequest(request.split()))));
	}

	@DeleteMapping("/{expenseId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(operationId = "deleteExpense", summary = "Delete an expense (own ones; admins any)",
			description = "Paid expenses are kept for the audit trail and leave lists and balances; planned ones are "
					+ "deleted.")
	void delete(@PathVariable UUID expenseId,
			@Parameter(description = "The version that was read; 409 VERSION_CONFLICT when it changed")
			@RequestParam(required = false) Long version) {
		expenses.delete(expenseId, version);
	}

	record CreateExpenseRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(
					max = Expense.MAX_DESCRIPTION_LENGTH) String description,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull UUID categoryId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Currency currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive @Max(
					Expense.MAX_AMOUNT_CENTS) Long amountCents,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Future dates are allowed")
			@NotNull LocalDate expenseDate,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ExpenseStatus status,
			@Schema(description = "Required when PAID; ignored when PLANNED") UUID paidByMemberId,
			@Schema(description = "When PAID; defaults to the expense date") LocalDate paidOn,
			@Size(max = Expense.MAX_NOTES_LENGTH) String notes,
			@Schema(description = "How to split it; by default equally among every active member") @Valid
			SplitBody split) {
	}

	record UpdateExpenseRequest(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "The version that was read") @NotNull @PositiveOrZero Long version,
			@Size(min = 1, max = Expense.MAX_DESCRIPTION_LENGTH) @Pattern(regexp = ".*\\S.*") String description,
			UUID categoryId, Currency currency, @Positive @Max(Expense.MAX_AMOUNT_CENTS) Long amountCents,
			LocalDate expenseDate, @Schema(description = "Paid expenses only") UUID paidByMemberId,
			@Schema(description = "Paid expenses only") LocalDate paidOn,
			@Schema(description = "Empty clears them") @Size(max = Expense.MAX_NOTES_LENGTH) String notes,
			@Schema(description = "A new split. Without it, equal and percentage splits follow a new amount; "
					+ "custom amounts must be sent again (422 SPLIT_SUM_MISMATCH otherwise).") @Valid SplitBody split) {
	}

	@Schema(description = "422 SPLIT_SUM_MISMATCH (with differenceCents) when custom amounts do not add up to the "
			+ "expense amount, SPLIT_PERCENT_SUM (with differenceBasisPoints) when percentages do not add up to 10000, "
			+ "SPLIT_INVALID for no or repeated members, MEMBER_INACTIVE for a member who left the band.")
	record SplitBody(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull SplitMode mode,
			@Schema(description = "EQUAL: the members (none means every active member); CUSTOM_AMOUNTS: amountCents "
					+ "per member; CUSTOM_PERCENTAGES: basisPoints per member") @Size(
							max = 100) List<@Valid @NotNull ShareBody> shares) {

		static Expenses.SplitRequest toRequest(SplitBody body) {
			if (body == null) {
				return null;
			}
			List<SplitCalculator.ShareInput> shares = body.shares() == null ? null
					: body.shares()
						.stream()
						.map(share -> new SplitCalculator.ShareInput(share.memberId(), share.amountCents(),
								share.basisPoints()))
						.toList();
			return new Expenses.SplitRequest(body.mode(), shares);
		}

	}

	record ShareBody(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull UUID memberId,
			@Schema(description = "CUSTOM_AMOUNTS only") @PositiveOrZero @Max(Expense.MAX_AMOUNT_CENTS) Long amountCents,
			@Schema(description = "CUSTOM_PERCENTAGES only; 10000 is 100%") @Min(0) @Max(10_000) Long basisPoints) {
	}

	record ShareResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID memberId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "This member's part, in cents")
			long shareCents, @Schema(description = "The amount given, for CUSTOM_AMOUNTS") Long amountCents,
			@Schema(description = "The percentage given, for CUSTOM_PERCENTAGES") Long basisPoints) {
	}

	record SplitResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) SplitMode mode,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Sorted by member id; the shares add up to the amount") List<ShareResponse> shares) {

		static SplitResponse of(Expense expense, List<ExpenseSplit> shares) {
			return new SplitResponse(expense.getSplitMode(),
					shares.stream()
						.map(share -> new ShareResponse(share.getMemberId(), share.getShareCents(),
								share.getInputCents(), share.getInputBasisPoints()))
						.toList());
		}

	}

	record AccessResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean edit,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean delete) {
	}

	record ExpenseResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String description,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID categoryId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Currency currency,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long amountCents,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate expenseDate,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) ExpenseStatus status, UUID paidByMemberId,
			LocalDate paidOn, String notes,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) SplitResponse split,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID createdBy,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "Send it back when changing the expense") long version,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
					description = "What the current user may do with this expense") AccessResponse access,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt) {

		static ExpenseResponse of(Expenses.ExpenseDetails details) {
			Expense expense = details.expense();
			return new ExpenseResponse(expense.getId(), expense.getDescription(), expense.getCategoryId(),
					expense.getCurrency(), expense.getAmountCents(), expense.getExpenseDate(), expense.getStatus(),
					expense.getPaidByMemberId(), expense.getPaidOn(), expense.getNotes(),
					SplitResponse.of(expense, details.shares()), expense.getCreatedBy(),
					expense.getVersion(), new AccessResponse(details.access().edit(), details.access().delete()),
					expense.getCreatedAt(), expense.getUpdatedAt());
		}

	}

}
