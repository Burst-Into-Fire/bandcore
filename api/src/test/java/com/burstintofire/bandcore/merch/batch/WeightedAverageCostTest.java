package com.burstintofire.bandcore.merch.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

class WeightedAverageCostTest {

	@Test
	void theFirstBatchSetsTheAverage() {
		// 100 shirts for R$ 3.000,00.
		assertThat(WeightedAverageCost.next(0, null, 100, 300_000)).isEqualTo(3000);
	}

	@Test
	void aSecondBatchAtAnotherCostMovesTheAverageInProportion() {
		// 100 shirts at R$ 30,00 on hand, then 50 at R$ 35,00: R$ 4.750,00 for 150 = R$ 31,666... → R$ 31,67.
		assertThat(WeightedAverageCost.next(100, 3000L, 50, 175_000)).isEqualTo(3167);
	}

	@Test
	void halfACentRoundsUp() {
		// (1 × 100 + 101) / 2 = 100,5 cents.
		assertThat(WeightedAverageCost.next(1, 100L, 1, 101)).isEqualTo(101);
		// (1 × 100 + 100) / 3 = 66,67 cents; (2 × 0 + 1) / 3 = 0,33 cents.
		assertThat(WeightedAverageCost.next(1, 100L, 2, 100)).isEqualTo(67);
		assertThat(WeightedAverageCost.next(2, 0L, 1, 1)).isZero();
	}

	@Test
	void withNothingOnHandTheNewUnitsSetTheAverageAlone() {
		// The last average is kept when stock reaches zero; the next batch replaces it.
		assertThat(WeightedAverageCost.next(0, 9999L, 3, 1000)).isEqualTo(333);
	}

	@Test
	void unitsWithoutAKnownCostTakeTheFirstBatchCost() {
		// 4 units entered by an adjustment before any batch.
		assertThat(WeightedAverageCost.next(4, null, 2, 5000)).isEqualTo(2500);
	}

	@Test
	void freeUnitsLowerTheAverage() {
		assertThat(WeightedAverageCost.next(10, 2000L, 10, 0)).isEqualTo(1000);
	}

	@Test
	void rejectsInvalidInput() {
		assertThatThrownBy(() -> WeightedAverageCost.next(-1, 100L, 1, 100)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> WeightedAverageCost.next(1, 100L, 0, 100)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> WeightedAverageCost.next(1, -1L, 1, 100)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> WeightedAverageCost.next(1, 100L, 1, -1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> WeightedAverageCost.next(1, 100L, 1, WeightedAverageCost.MAX_CENTS + 1))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Property
	void theAverageLiesBetweenTheCheapestAndTheDearestUnitCost(
			@ForAll @IntRange(min = 1, max = 1_000_000) int onHand,
			@ForAll @LongRange(min = 0, max = 100_000_000) long average,
			@ForAll @IntRange(min = 1, max = 100_000) int quantityIn,
			@ForAll @LongRange(min = 0, max = 1_000_000_000_000L) long lineCost) {
		long result = WeightedAverageCost.next(onHand, average, quantityIn, lineCost);

		// The unit cost of what comes in is lineCost / quantityIn, rarely a whole number of cents.
		long cheapestIn = lineCost / quantityIn;
		long dearestIn = (lineCost + quantityIn - 1) / quantityIn;
		assertThat(result).isBetween(Math.min(average, cheapestIn), Math.max(average, dearestIn));
	}

	@Property
	void theTotalValueOfTheStockIsKeptToWithinHalfACentPerUnit(
			@ForAll @IntRange(min = 1, max = 1_000_000) int onHand,
			@ForAll @LongRange(min = 0, max = 100_000_000) long average,
			@ForAll @IntRange(min = 1, max = 100_000) int quantityIn,
			@ForAll @LongRange(min = 0, max = 1_000_000_000_000L) long lineCost) {
		long result = WeightedAverageCost.next(onHand, average, quantityIn, lineCost);

		// |result × units − (value before + line cost)| ≤ units / 2.
		BigInteger units = BigInteger.valueOf((long) onHand + quantityIn);
		BigInteger exact = BigInteger.valueOf(onHand)
			.multiply(BigInteger.valueOf(average))
			.add(BigInteger.valueOf(lineCost));
		BigInteger error = BigInteger.valueOf(result).multiply(units).subtract(exact).abs();
		assertThat(error.multiply(BigInteger.TWO)).isLessThanOrEqualTo(units);
	}

}
