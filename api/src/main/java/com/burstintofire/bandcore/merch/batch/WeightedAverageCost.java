package com.burstintofire.bandcore.merch.batch;

import java.math.BigInteger;

/**
 * The weighted average unit cost of a variant after units come in (ADR-0009):
 *
 * <pre>
 * new_average = (quantity_on_hand × average + line_cost) / (quantity_on_hand + quantity_in)
 * </pre>
 *
 * in integer cents, rounded half-up. With no average yet, or nothing on hand, the units that come
 * in set it alone: {@code line_cost / quantity_in}. Units on hand without a known cost (entered by
 * an adjustment before any batch) take the cost of the first batch.
 */
final class WeightedAverageCost {

	/** Far above any merch cost; with it, nothing below overflows. */
	static final long MAX_CENTS = 1_000_000_000_000L;

	private static final BigInteger TWO = BigInteger.TWO;

	private WeightedAverageCost() {
	}

	/**
	 * @param quantityOnHand units in stock before, at their {@code average}
	 * @param average the current average unit cost in cents, or null when unknown
	 * @param quantityIn units coming in, at least 1
	 * @param lineCostCents what the units coming in cost together
	 */
	static long next(int quantityOnHand, Long average, int quantityIn, long lineCostCents) {
		if (quantityOnHand < 0 || quantityIn < 1) {
			throw new IllegalArgumentException("Quantities must be positive");
		}
		if (lineCostCents < 0 || lineCostCents > MAX_CENTS || (average != null && (average < 0 || average > MAX_CENTS))) {
			throw new IllegalArgumentException("Costs must be between 0 and " + MAX_CENTS + " cents");
		}
		if (average == null || quantityOnHand == 0) {
			return divideHalfUp(BigInteger.valueOf(lineCostCents), BigInteger.valueOf(quantityIn));
		}
		BigInteger onHand = BigInteger.valueOf(quantityOnHand);
		BigInteger total = onHand.multiply(BigInteger.valueOf(average)).add(BigInteger.valueOf(lineCostCents));
		return divideHalfUp(total, onHand.add(BigInteger.valueOf(quantityIn)));
	}

	/** {@code value / divisor} rounded half-up, for non-negative values. */
	static long divideHalfUp(BigInteger value, BigInteger divisor) {
		return value.multiply(TWO).add(divisor).divide(divisor.multiply(TWO)).longValueExact();
	}

}
