package com.burstintofire.bandcore.finance.balance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.burstintofire.bandcore.shared.money.Currency;

/**
 * Turns per-member totals into balances, one list per currency (RN02, RN08). Amounts in
 * different currencies are never added. Active members are always listed (with zero when nothing
 * moved for them); members who left the band only while their balance is not zero.
 */
final class BalanceCalculator {

	enum Direction {

		/** The band owes the member. */
		RECEIVES,

		/** The member owes the band. */
		OWES,

		SETTLED

	}

	record MemberBalance(UUID memberId, long balanceCents, Direction direction, long paidCents, long shareCents,
			long sentCents, long receivedCents, boolean active) {

		static MemberBalance of(MemberTotals totals, boolean active) {
			long balance = totals.balanceCents();
			Direction direction = balance > 0 ? Direction.RECEIVES : balance < 0 ? Direction.OWES : Direction.SETTLED;
			return new MemberBalance(totals.memberId(), balance, direction, totals.paidCents(), totals.shareCents(),
					totals.sentCents(), totals.receivedCents(), active);
		}

	}

	record CurrencyBalances(Currency currency, List<MemberBalance> members) {
	}

	/** Who receives first, by amount; then who is settled; then who owes; ties by member id. */
	static final Comparator<MemberBalance> ORDER = Comparator.comparingLong(MemberBalance::balanceCents)
		.reversed()
		.thenComparing(MemberBalance::memberId, Comparator.comparing(UUID::toString));

	private BalanceCalculator() {
	}

	/**
	 * @param totals rows per member and currency; several rows for the same pair are added up
	 * @param active whether each member is active; members missing from it count as inactive
	 * @param activeMembers every active member, listed in each currency even without movements
	 */
	static List<CurrencyBalances> balances(List<MemberTotals> totals, Map<UUID, Boolean> active,
			List<UUID> activeMembers) {
		Map<Currency, Map<UUID, MemberTotals>> byCurrency = new EnumMap<>(Currency.class);
		for (MemberTotals row : totals) {
			byCurrency.computeIfAbsent(row.currency(), currency -> new LinkedHashMap<>())
				.merge(row.memberId(), row, MemberTotals::plus);
		}
		List<CurrencyBalances> result = new ArrayList<>();
		for (Map.Entry<Currency, Map<UUID, MemberTotals>> entry : byCurrency.entrySet()) {
			Map<UUID, MemberTotals> members = entry.getValue();
			for (UUID memberId : activeMembers) {
				members.putIfAbsent(memberId, MemberTotals.zero(memberId, entry.getKey()));
			}
			List<MemberBalance> balances = members.values()
				.stream()
				.map(row -> MemberBalance.of(row, active.getOrDefault(row.memberId(), false)))
				.filter(balance -> balance.active() || balance.balanceCents() != 0)
				.sorted(ORDER)
				.toList();
			result.add(new CurrencyBalances(entry.getKey(), balances));
		}
		return List.copyOf(result);
	}

}
