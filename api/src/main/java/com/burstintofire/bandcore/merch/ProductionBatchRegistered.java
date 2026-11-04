package com.burstintofire.bandcore.merch;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.burstintofire.bandcore.shared.money.Currency;

/**
 * A production batch was received, inside the transaction that registers it. When the admin
 * asked for it, {@link #expense()} describes the expense that pays for the batch: the finance
 * module listens synchronously and creates it in the same transaction (M7-04), so the batch and
 * its expense are saved together or not at all. The types are merch's own: merch never depends
 * on finance.
 *
 * @param totalCostCents what the batch cost, in {@code currency}: the amount of the expense
 * @param expense null when no expense was asked for
 */
public record ProductionBatchRegistered(UUID bandId, UUID batchId, Currency currency, long totalCostCents,
		LocalDate receivedOn, ExpenseDraft expense) {

	/**
	 * The expense to create for the batch. Its amount and currency are the batch's.
	 *
	 * @param expenseDate null for the batch's received date
	 * @param paidByMemberId the member who paid, when {@code paid}
	 * @param paidOn when {@code paid}; null for the expense date
	 * @param shares as for any expense: none with EQUAL means every active member
	 */
	public record ExpenseDraft(String description, UUID categoryId, LocalDate expenseDate, boolean paid,
			UUID paidByMemberId, LocalDate paidOn, String notes, ExpenseSplitMode splitMode, List<Share> shares) {
	}

	/** How the expense is split, as for any expense. */
	public enum ExpenseSplitMode {

		EQUAL, CUSTOM_AMOUNTS, CUSTOM_PERCENTAGES

	}

	/** A member's part: the member only (EQUAL), an amount or a percentage in basis points. */
	public record Share(UUID memberId, Long amountCents, Long basisPoints) {
	}

}
