package com.burstintofire.bandcore.finance.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import com.burstintofire.bandcore.finance.balance.BalanceCalculator.Direction;
import com.burstintofire.bandcore.finance.balance.BalanceCalculator.MemberBalance;
import com.burstintofire.bandcore.finance.balance.SettlementPlanner.Transfer;

class SettlementPlannerTest {

	static final List<UUID> MEMBERS = IntStream.range(0, 12)
		.mapToObj(i -> UUID.fromString("00000000-0000-7000-8000-%012d".formatted(i)))
		.toList();

	@Test
	void theMockupNeedsThreeTransfersToTheVocalist() {
		List<Transfer> plan = SettlementPlanner.plan(balances(202_500, -67_500, -67_500, -67_500));

		assertThat(plan).containsExactly(new Transfer(MEMBERS.get(1), MEMBERS.get(0), 67_500),
				new Transfer(MEMBERS.get(2), MEMBERS.get(0), 67_500), new Transfer(MEMBERS.get(3), MEMBERS.get(0), 67_500));
	}

	@Test
	void theLargestDebtorPaysTheLargestCreditorFirst() {
		List<Transfer> plan = SettlementPlanner.plan(balances(500, 300, -600, -200));

		// After the first transfer, member 3 (owes 200) is the largest debtor, ahead of member 2 (owes 100).
		assertThat(plan).containsExactly(new Transfer(MEMBERS.get(2), MEMBERS.get(0), 500),
				new Transfer(MEMBERS.get(3), MEMBERS.get(1), 200), new Transfer(MEMBERS.get(2), MEMBERS.get(1), 100));
	}

	@Test
	void settledBalancesNeedNoTransfers() {
		assertThat(SettlementPlanner.plan(balances(0, 0, 0))).isEmpty();
		assertThat(SettlementPlanner.plan(List.of())).isEmpty();
	}

	@Test
	void balancesThatDoNotAddUpToZeroAreRejected() {
		assertThatThrownBy(() -> SettlementPlanner.plan(balances(100, -99))).isInstanceOf(IllegalArgumentException.class);
	}

	@Property
	void applyingThePlanSettlesEveryone(@ForAll("zeroSum") List<Long> amounts) {
		List<MemberBalance> balances = balances(amounts);
		Map<UUID, Long> remaining = new HashMap<>();
		balances.forEach(balance -> remaining.put(balance.memberId(), balance.balanceCents()));

		for (Transfer transfer : SettlementPlanner.plan(balances)) {
			assertThat(transfer.amountCents()).isPositive();
			remaining.merge(transfer.fromMemberId(), transfer.amountCents(), Long::sum);
			remaining.merge(transfer.toMemberId(), -transfer.amountCents(), Long::sum);
		}

		assertThat(remaining.values()).containsOnly(0L);
	}

	@Property
	void atMostOneTransferLessThanThePeopleInvolved(@ForAll("zeroSum") List<Long> amounts) {
		long involved = amounts.stream().filter(amount -> amount != 0).count();

		assertThat(SettlementPlanner.plan(balances(amounts)).size()).isLessThanOrEqualTo((int) Math.max(0, involved - 1));
	}

	@Property
	void theSameBalancesGiveTheSamePlanInAnyOrder(@ForAll("zeroSum") List<Long> amounts) {
		List<MemberBalance> balances = balances(amounts);
		List<MemberBalance> shuffled = new ArrayList<>(balances);
		Collections.reverse(shuffled);

		assertThat(SettlementPlanner.plan(shuffled)).isEqualTo(SettlementPlanner.plan(balances));
	}

	/** Up to 12 members whose balances add up to zero. */
	@Provide
	Arbitrary<List<Long>> zeroSum() {
		return Arbitraries.longs()
			.between(-1_000_000, 1_000_000)
			.list()
			.ofMinSize(1)
			.ofMaxSize(MEMBERS.size() - 1)
			.map(list -> {
				List<Long> withLast = new ArrayList<>(list);
				withLast.add(-list.stream().mapToLong(Long::longValue).sum());
				return withLast;
			});
	}

	private static List<MemberBalance> balances(long... amounts) {
		List<Long> list = new ArrayList<>();
		for (long amount : amounts) {
			list.add(amount);
		}
		return balances(list);
	}

	private static List<MemberBalance> balances(List<Long> amounts) {
		List<MemberBalance> balances = new ArrayList<>();
		for (int i = 0; i < amounts.size(); i++) {
			long cents = amounts.get(i);
			Direction direction = cents > 0 ? Direction.RECEIVES : cents < 0 ? Direction.OWES : Direction.SETTLED;
			balances.add(new MemberBalance(MEMBERS.get(i), cents, direction, 0, 0, 0, 0, true));
		}
		return balances;
	}

}
