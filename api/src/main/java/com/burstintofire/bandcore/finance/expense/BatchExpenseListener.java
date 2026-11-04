package com.burstintofire.bandcore.finance.expense;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.burstintofire.bandcore.merch.ProductionBatchRegistered;
import com.burstintofire.bandcore.shared.tenant.TenantContextHolder;
import com.burstintofire.bandcore.shared.web.ApiException;

/**
 * Turns the cost of a production batch into a split expense (M7-04, RF34) when the admin asked
 * for one. Synchronous on purpose: it runs inside the batch's transaction, with the admin's band
 * and identity, so the batch and its expense are saved together or not at all, and an expense the
 * rules refuse answers right away with errors on the {@code expense.*} fields.
 */
@Component
class BatchExpenseListener {

	private final Expenses expenses;

	BatchExpenseListener(Expenses expenses) {
		this.expenses = expenses;
	}

	@EventListener
	void on(ProductionBatchRegistered event) {
		ProductionBatchRegistered.ExpenseDraft draft = event.expense();
		if (draft == null) {
			return;
		}
		if (!event.bandId().equals(TenantContextHolder.require().bandId())) {
			throw new IllegalStateException("A batch's expense belongs to the batch's band");
		}
		Expenses.NewExpense expense = new Expenses.NewExpense(draft.description(), draft.categoryId(), event.currency(),
				event.totalCostCents(), draft.expenseDate() != null ? draft.expenseDate() : event.receivedOn(),
				draft.paid() ? ExpenseStatus.PAID : ExpenseStatus.PLANNED, draft.paidByMemberId(), draft.paidOn(),
				draft.notes(), split(draft));
		try {
			expenses.createFromSource(ExpenseSourceType.BATCH, event.batchId(), expense);
		}
		catch (ApiException refused) {
			throw onExpenseFields(refused);
		}
	}

	private static Expenses.SplitRequest split(ProductionBatchRegistered.ExpenseDraft draft) {
		List<SplitCalculator.ShareInput> shares = draft.shares() == null ? null
				: draft.shares()
					.stream()
					.map(share -> new SplitCalculator.ShareInput(share.memberId(), share.amountCents(),
							share.basisPoints()))
					.toList();
		return new Expenses.SplitRequest(SplitMode.valueOf(draft.splitMode().name()), shares);
	}

	/** The batch request nests the expense: point its errors at {@code expense.<field>}. */
	private static ApiException onExpenseFields(ApiException refused) {
		if (refused.field() == null) {
			return refused;
		}
		ApiException nested = new ApiException(refused.status(), refused.code(), refused.getMessage(),
				"expense." + refused.field());
		refused.properties().forEach(nested::withProperty);
		return nested;
	}

}
