package com.burstintofire.bandcore.finance.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.burstintofire.bandcore.finance.expense.SplitCalculator.Share;
import com.burstintofire.bandcore.finance.expense.SplitCalculator.ShareInput;
import com.burstintofire.bandcore.shared.web.ApiException;
import com.burstintofire.bandcore.shared.web.ErrorCode;

class SplitCalculatorTest {

	static final UUID A = UUID.fromString("00000000-0000-7000-8000-00000000000a");

	static final UUID B = UUID.fromString("00000000-0000-7000-8000-00000000000b");

	// Starts with "f": sorts last as text, although UUID.compareTo would put it first (negative msb).
	static final UUID C = UUID.fromString("f0000000-0000-7000-8000-00000000000c");

	@Test
	void equalSplitGivesTheExtraCentsToTheFirstMembersById() {
		List<Share> shares = SplitCalculator.compute(SplitMode.EQUAL, 10_000,
				List.of(input(C), input(B), input(A)));

		assertThat(shares).extracting(Share::memberId).containsExactly(A, B, C);
		assertThat(shares).extracting(Share::shareCents).containsExactly(3334L, 3333L, 3333L);
		assertThat(shares).allMatch(share -> share.inputCents() == null && share.inputBasisPoints() == null);
	}

	@Test
	void customAmountsMustAddUpToTheTotal() {
		List<Share> shares = SplitCalculator.compute(SplitMode.CUSTOM_AMOUNTS, 1000,
				List.of(amount(A, 700), amount(B, 300), amount(C, 0)));
		assertThat(shares).extracting(Share::shareCents).containsExactly(700L, 300L, 0L);
		assertThat(shares).extracting(Share::inputCents).containsExactly(700L, 300L, 0L);

		assertThatThrownBy(() -> SplitCalculator.compute(SplitMode.CUSTOM_AMOUNTS, 1000,
				List.of(amount(A, 700), amount(B, 250))))
			.isInstanceOfSatisfying(ApiException.class, problem -> {
				assertThat(problem.code()).isEqualTo(ErrorCode.SPLIT_SUM_MISMATCH);
				assertThat(problem.properties()).containsEntry("differenceCents", 50L);
			});
		assertThatThrownBy(() -> SplitCalculator.compute(SplitMode.CUSTOM_AMOUNTS, 1000,
				List.of(amount(A, 1100))))
			.isInstanceOfSatisfying(ApiException.class,
					problem -> assertThat(problem.properties()).containsEntry("differenceCents", -100L));
	}

	@Test
	void percentagesMustAddUpToAHundredAndBecomeExactCents() {
		List<Share> shares = SplitCalculator.compute(SplitMode.CUSTOM_PERCENTAGES, 100,
				List.of(percent(A, 3333), percent(B, 3333), percent(C, 3334)));
		assertThat(shares).extracting(Share::shareCents).containsExactly(33L, 33L, 34L);
		assertThat(shares).extracting(Share::inputBasisPoints).containsExactly(3333L, 3333L, 3334L);

		assertThatThrownBy(() -> SplitCalculator.compute(SplitMode.CUSTOM_PERCENTAGES, 100,
				List.of(percent(A, 5000), percent(B, 4000))))
			.isInstanceOfSatisfying(ApiException.class, problem -> {
				assertThat(problem.code()).isEqualTo(ErrorCode.SPLIT_PERCENT_SUM);
				assertThat(problem.properties()).containsEntry("differenceBasisPoints", 1000L);
			});
	}

	@Test
	void invalidSplitsAreRejected() {
		assertCode(() -> SplitCalculator.compute(SplitMode.EQUAL, 100, List.of()), ErrorCode.SPLIT_INVALID);
		assertCode(() -> SplitCalculator.compute(SplitMode.EQUAL, 100, List.of(input(A), input(A))),
				ErrorCode.SPLIT_INVALID);
		assertCode(() -> SplitCalculator.compute(SplitMode.CUSTOM_AMOUNTS, 100, List.of(input(A))),
				ErrorCode.SPLIT_INVALID);
		assertCode(() -> SplitCalculator.compute(SplitMode.CUSTOM_AMOUNTS, 100, List.of(amount(A, -1))),
				ErrorCode.SPLIT_INVALID);
		assertCode(() -> SplitCalculator.compute(SplitMode.CUSTOM_PERCENTAGES, 100, List.of(amount(A, 100))),
				ErrorCode.SPLIT_INVALID);
		assertCode(() -> SplitCalculator.compute(SplitMode.CUSTOM_PERCENTAGES, 100, List.of(percent(A, 10_001))),
				ErrorCode.SPLIT_INVALID);
	}

	@Test
	void savedInputsRecomputeForANewAmount() {
		List<Share> percentages = SplitCalculator.compute(SplitMode.CUSTOM_PERCENTAGES, 100,
				List.of(percent(A, 2500), percent(B, 7500)));

		assertThat(SplitCalculator.compute(SplitMode.CUSTOM_PERCENTAGES, 1001, SplitCalculator.inputsOf(percentages)))
			.extracting(Share::shareCents)
			.containsExactly(250L, 751L);
	}

	private static void assertCode(Runnable action, ErrorCode code) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
				problem -> assertThat(problem.code()).isEqualTo(code));
	}

	private static ShareInput input(UUID member) {
		return new ShareInput(member, null, null);
	}

	private static ShareInput amount(UUID member, long cents) {
		return new ShareInput(member, cents, null);
	}

	private static ShareInput percent(UUID member, long basisPoints) {
		return new ShareInput(member, null, basisPoints);
	}

}
