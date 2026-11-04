package com.burstintofire.bandcore.finance.balance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * Suggests transfers that settle every balance of one currency (RF19): the largest debtor pays
 * the largest creditor as much as both allow, until nobody owes. Each transfer settles at least
 * one member, so n members need at most n - 1 transfers. Ties go to the member whose id comes
 * first as text, so the same balances always give the same plan.
 */
final class SettlementPlanner {

	record Transfer(UUID fromMemberId, UUID toMemberId, long amountCents) {
	}

	private record Open(UUID memberId, long cents) {
	}

	/** Largest amount first, then member id as text. */
	private static final Comparator<Open> LARGEST_FIRST = Comparator.comparingLong(Open::cents)
		.reversed()
		.thenComparing(Open::memberId, Comparator.comparing(UUID::toString));

	private SettlementPlanner() {
	}

	/**
	 * @param balances one currency's balances, adding up to zero (positive: receives)
	 * @throws IllegalArgumentException when they do not add up to zero
	 */
	static List<Transfer> plan(List<BalanceCalculator.MemberBalance> balances) {
		long sum = 0;
		PriorityQueue<Open> creditors = new PriorityQueue<>(LARGEST_FIRST);
		PriorityQueue<Open> debtors = new PriorityQueue<>(LARGEST_FIRST);
		for (BalanceCalculator.MemberBalance balance : balances) {
			sum = Math.addExact(sum, balance.balanceCents());
			if (balance.balanceCents() > 0) {
				creditors.add(new Open(balance.memberId(), balance.balanceCents()));
			}
			else if (balance.balanceCents() < 0) {
				debtors.add(new Open(balance.memberId(), Math.negateExact(balance.balanceCents())));
			}
		}
		if (sum != 0) {
			throw new IllegalArgumentException("Balances add up to " + sum + " cents, not zero");
		}
		List<Transfer> transfers = new ArrayList<>();
		while (!debtors.isEmpty()) {
			Open debtor = debtors.poll();
			Open creditor = creditors.poll();
			long amount = Math.min(debtor.cents(), creditor.cents());
			transfers.add(new Transfer(debtor.memberId(), creditor.memberId(), amount));
			if (debtor.cents() > amount) {
				debtors.add(new Open(debtor.memberId(), debtor.cents() - amount));
			}
			if (creditor.cents() > amount) {
				creditors.add(new Open(creditor.memberId(), creditor.cents() - amount));
			}
		}
		return List.copyOf(transfers);
	}

}
