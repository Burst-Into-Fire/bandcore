package com.burstintofire.bandcore.finance.balance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import com.burstintofire.bandcore.finance.balance.BalanceCalculator.CurrencyBalances;
import com.burstintofire.bandcore.finance.balance.BalanceCalculator.Direction;
import com.burstintofire.bandcore.finance.balance.BalanceCalculator.MemberBalance;
import com.burstintofire.bandcore.shared.money.Allocation;
import com.burstintofire.bandcore.shared.money.Currency;

class BalanceCalculatorTest {

	static final List<UUID> MEMBERS = IntStream.range(0, 6)
		.mapToObj(i -> UUID.fromString("00000000-0000-7000-8000-00000000000" + i))
		.toList();

	static final UUID VOCALIST = MEMBERS.get(0);

	@Test
	void theMockupScenario() {
		// The vocalist paid R$ 2.700,00 split among the four members.
		List<MemberTotals> totals = new ArrayList<>();
		totals.add(new MemberTotals(VOCALIST, Currency.BRL, 270_000, 0, 0, 0));
		for (UUID member : MEMBERS.subList(0, 4)) {
			totals.add(new MemberTotals(member, Currency.BRL, 0, 67_500, 0, 0));
		}

		List<CurrencyBalances> balances = BalanceCalculator.balances(totals, active(4), MEMBERS.subList(0, 4));

		assertThat(balances).hasSize(1);
		List<MemberBalance> members = balances.getFirst().members();
		assertThat(members).extracting(MemberBalance::balanceCents).containsExactly(202_500L, -67_500L, -67_500L,
				-67_500L);
		assertThat(members.getFirst().memberId()).isEqualTo(VOCALIST);
		assertThat(members.getFirst().direction()).isEqualTo(Direction.RECEIVES);
		assertThat(members.get(1).direction()).isEqualTo(Direction.OWES);
	}

	@Test
	void settlementsMoveBothSides() {
		List<MemberTotals> totals = List.of(new MemberTotals(VOCALIST, Currency.BRL, 10_000, 5_000, 0, 5_000),
				new MemberTotals(MEMBERS.get(1), Currency.BRL, 0, 5_000, 5_000, 0));

		List<MemberBalance> members = BalanceCalculator.balances(totals, active(2), MEMBERS.subList(0, 2))
			.getFirst()
			.members();

		assertThat(members).extracting(MemberBalance::direction).containsOnly(Direction.SETTLED);
	}

	@Test
	void currenciesAreNeverMixed() {
		List<MemberTotals> totals = List.of(new MemberTotals(VOCALIST, Currency.USD, 1_000, 500, 0, 0),
				new MemberTotals(MEMBERS.get(1), Currency.USD, 0, 500, 0, 0),
				new MemberTotals(VOCALIST, Currency.BRL, 0, 300, 0, 0),
				new MemberTotals(MEMBERS.get(1), Currency.BRL, 300, 0, 0, 0));

		List<CurrencyBalances> balances = BalanceCalculator.balances(totals, active(2), MEMBERS.subList(0, 2));

		assertThat(balances).extracting(CurrencyBalances::currency).containsExactly(Currency.BRL, Currency.USD);
		assertThat(balances.get(0).members()).extracting(MemberBalance::balanceCents).containsExactly(300L, -300L);
		assertThat(balances.get(1).members()).extracting(MemberBalance::balanceCents).containsExactly(500L, -500L);
	}

	@Test
	void activeMembersAlwaysAppearAndThoseWhoLeftOnlyWhileTheyHaveABalance() {
		UUID left = MEMBERS.get(4);
		UUID leftSettled = MEMBERS.get(5);
		List<MemberTotals> totals = List.of(new MemberTotals(VOCALIST, Currency.BRL, 1_000, 0, 0, 0),
				new MemberTotals(left, Currency.BRL, 0, 1_000, 0, 0),
				new MemberTotals(leftSettled, Currency.BRL, 500, 500, 0, 0));
		Map<UUID, Boolean> status = Map.of(VOCALIST, true, MEMBERS.get(1), true, left, false, leftSettled, false);

		List<MemberBalance> members = BalanceCalculator.balances(totals, status, MEMBERS.subList(0, 2))
			.getFirst()
			.members();

		assertThat(members).extracting(MemberBalance::memberId).containsExactly(VOCALIST, MEMBERS.get(1), left);
		assertThat(members.get(1).direction()).isEqualTo(Direction.SETTLED);
		assertThat(members.get(2).active()).isFalse();
	}

	@Test
	void noMovementsMeansNoCurrencies() {
		assertThat(BalanceCalculator.balances(List.of(), active(3), MEMBERS.subList(0, 3))).isEmpty();
	}

	/** RN02: for any paid expenses, shares and settlements, each currency's balances add up to zero. */
	@Property
	void balancesAlwaysAddUpToZero(@ForAll("ledgers") List<MemberTotals> ledger) {
		List<CurrencyBalances> balances = BalanceCalculator.balances(ledger, active(MEMBERS.size()), MEMBERS);

		for (CurrencyBalances currency : balances) {
			assertThat(currency.members().stream().mapToLong(MemberBalance::balanceCents).sum())
				.as(currency.currency().name())
				.isZero();
		}
	}

	/** Rows as the ledger query would produce them, one per movement (the calculator adds them up). */
	@Provide
	Arbitrary<List<MemberTotals>> ledgers() {
		Arbitrary<Currency> currencies = Arbitraries.of(Currency.class);
		Arbitrary<UUID> members = Arbitraries.of(MEMBERS);
		Arbitrary<List<MemberTotals>> expense = Combinators
			.combine(currencies, members, Arbitraries.longs().between(1, 10_000_000),
					members.set().ofMinSize(1).ofMaxSize(MEMBERS.size()))
			.as((currency, payer, amount, sharing) -> {
				List<UUID> sorted = sharing.stream().sorted().toList();
				long[] shares = Allocation.equal(amount, sorted.size());
				List<MemberTotals> rows = new ArrayList<>();
				rows.add(new MemberTotals(payer, currency, amount, 0, 0, 0));
				for (int i = 0; i < shares.length; i++) {
					rows.add(new MemberTotals(sorted.get(i), currency, 0, shares[i], 0, 0));
				}
				return rows;
			});
		Arbitrary<List<MemberTotals>> settlement = Combinators
			.combine(currencies, members, members, Arbitraries.longs().between(1, 1_000_000))
			.filter((currency, from, to, amount) -> !from.equals(to))
			.as((currency, from, to, amount) -> List.of(new MemberTotals(from, currency, 0, 0, amount, 0),
					new MemberTotals(to, currency, 0, 0, 0, amount)));
		return Arbitraries.oneOf(expense, settlement)
			.list()
			.ofMaxSize(30)
			.map(movements -> movements.stream().flatMap(List::stream).toList());
	}

	private static Map<UUID, Boolean> active(int count) {
		return MEMBERS.subList(0, count)
			.stream()
			.collect(java.util.stream.Collectors.toMap(member -> member, member -> true));
	}

}
