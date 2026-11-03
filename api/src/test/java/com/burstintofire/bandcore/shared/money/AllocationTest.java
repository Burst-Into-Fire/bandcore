package com.burstintofire.bandcore.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

class AllocationTest {

	@Test
	void hundredReaisByThreeAlwaysGivesTheExtraCentToTheFirst() {
		assertThat(Allocation.equal(10_000, 3)).containsExactly(3334, 3333, 3333);
		assertThat(Money.of(10_000, Currency.BRL).allocateEqually(3)).containsExactly(Money.of(3334, Currency.BRL),
				Money.of(3333, Currency.BRL), Money.of(3333, Currency.BRL));
	}

	@Test
	void rejectsInvalidInput() {
		assertThatThrownBy(() -> Allocation.equal(100, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Allocation.equal(-1, 2)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Allocation.byWeights(100, new long[0])).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Allocation.byWeights(100, new long[] { 0, 0 }))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Allocation.byWeights(100, new long[] { 5000, -1 }))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void overflowThrows() {
		assertThatThrownBy(() -> Allocation.byWeights(Long.MAX_VALUE, new long[] { 2, 1 }))
			.isInstanceOf(ArithmeticException.class);
	}

	@Property
	void equalPartsAlwaysSumToTheTotal(@ForAll("totals") long total, @ForAll @IntRange(min = 1, max = 50) int parts) {
		assertThat(Arrays.stream(Allocation.equal(total, parts)).sum()).isEqualTo(total);
	}

	@Property
	void equalPartsDifferByAtMostOneCent(@ForAll("totals") long total,
			@ForAll @IntRange(min = 1, max = 50) int parts) {
		long[] result = Allocation.equal(total, parts);
		long min = Arrays.stream(result).min().orElseThrow();
		long max = Arrays.stream(result).max().orElseThrow();
		assertThat(max - min).isLessThanOrEqualTo(1);
	}

	@Property
	void weightedPartsAlwaysSumToTheTotal(@ForAll("totals") long total, @ForAll("weights") List<Long> weights) {
		assertThat(Arrays.stream(Allocation.byWeights(total, toArray(weights))).sum()).isEqualTo(total);
	}

	@Property
	void weightedPartsAreWithinOneCentOfTheExactProportion(@ForAll("totals") long total,
			@ForAll("weights") List<Long> weights) {
		long[] array = toArray(weights);
		long weightSum = Arrays.stream(array).sum();
		long[] result = Allocation.byWeights(total, array);
		for (int i = 0; i < array.length; i++) {
			// |part - total * weight / weightSum| < 1, without leaving integers
			long distance = Math.abs(result[i] * weightSum - total * array[i]);
			assertThat(distance).isLessThan(weightSum);
		}
	}

	@Property
	void theSameInputAlwaysGivesTheSameOutput(@ForAll("totals") long total, @ForAll("weights") List<Long> weights) {
		assertThat(Allocation.byWeights(total, toArray(weights))).containsExactly(Allocation.byWeights(total,
				toArray(weights)));
	}

	@Property
	void noPartIsNegative(@ForAll("totals") long total, @ForAll("weights") List<Long> weights) {
		assertThat(Arrays.stream(Allocation.byWeights(total, toArray(weights))).min().orElseThrow()).isNotNegative();
	}

	@Property
	void basisPointsSummingToAHundredPercent(@ForAll @LongRange(min = 0, max = 100_000_000) long total,
			@ForAll @IntRange(min = 0, max = 10_000) int first) {
		long[] result = Allocation.byWeights(total, new long[] { first, Allocation.BASIS_POINTS - first });
		assertThat(result[0] + result[1]).isEqualTo(total);
	}

	@Property
	void zeroWeightsGetNothing(@ForAll("totals") long total, @ForAll("weights") List<Long> weights) {
		long[] array = toArray(weights);
		Assume.that(Arrays.stream(array).anyMatch(weight -> weight == 0));
		long[] result = Allocation.byWeights(total, array);
		for (int i = 0; i < array.length; i++) {
			if (array[i] == 0) {
				assertThat(result[i]).isZero();
			}
		}
	}

	@Provide
	Arbitrary<Long> totals() {
		// Up to R$ 1 billion: total * weight stays far from overflow with weights up to 10000.
		return Arbitraries.longs().between(0, 100_000_000_000L).edgeCases(edges -> edges.includeOnly(0L, 1L));
	}

	@Provide
	Arbitrary<List<Long>> weights() {
		return Arbitraries.longs()
			.between(0, Allocation.BASIS_POINTS)
			.list()
			.ofMinSize(1)
			.ofMaxSize(20)
			.filter(list -> list.stream().anyMatch(weight -> weight > 0));
	}

	private static long[] toArray(List<Long> weights) {
		return weights.stream().mapToLong(Long::longValue).toArray();
	}

}
