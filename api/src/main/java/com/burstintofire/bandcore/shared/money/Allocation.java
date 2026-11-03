package com.burstintofire.bandcore.shared.money;

import java.util.Arrays;

/**
 * Splits an amount of cents into parts that always add up exactly to the total (RN01), using the
 * largest remainder method: every part first gets the floor of its exact share, and the cents
 * left over go one each to the parts with the largest remainders. Ties go to the lower index, so
 * the same input always gives the same output: callers pass parts in a stable order (for
 * example, members sorted by id).
 *
 * <p>The same algorithm runs in the web app ({@code web/src/lib/allocation.ts}); both are tested
 * against {@code contracts/money/allocation.json}.
 */
public final class Allocation {

	/** Percentages are given in basis points: 10000 is 100%. */
	public static final long BASIS_POINTS = 10_000;

	private Allocation() {
	}

	/**
	 * {@code equal(10000, 3)} gives {@code [3334, 3333, 3333]}.
	 * @param total non-negative cents
	 * @param parts at least one
	 */
	public static long[] equal(long total, int parts) {
		if (parts < 1) {
			throw new IllegalArgumentException("At least one part is needed");
		}
		long[] weights = new long[parts];
		Arrays.fill(weights, 1);
		return byWeights(total, weights);
	}

	/**
	 * {@code byWeights(100, [3333, 3333, 3334])} gives {@code [33, 33, 34]}. Weights are
	 * relative: they do not have to sum to {@link #BASIS_POINTS}.
	 * @param total non-negative cents
	 * @param weights non-negative, at least one of them positive
	 * @throws ArithmeticException when {@code total * weight} overflows a long
	 */
	public static long[] byWeights(long total, long[] weights) {
		if (total < 0) {
			throw new IllegalArgumentException("The total must not be negative");
		}
		if (weights.length == 0) {
			throw new IllegalArgumentException("At least one weight is needed");
		}
		long weightSum = 0;
		for (long weight : weights) {
			if (weight < 0) {
				throw new IllegalArgumentException("Weights must not be negative");
			}
			weightSum = Math.addExact(weightSum, weight);
		}
		if (weightSum == 0) {
			throw new IllegalArgumentException("At least one weight must be positive");
		}

		long[] parts = new long[weights.length];
		long[] remainders = new long[weights.length];
		long allocated = 0;
		for (int i = 0; i < weights.length; i++) {
			long scaled = Math.multiplyExact(total, weights[i]);
			parts[i] = scaled / weightSum;
			remainders[i] = scaled % weightSum;
			allocated += parts[i];
		}

		// Fewer cents are left than parts with a positive remainder, so each gets at most one.
		long left = total - allocated;
		while (left > 0) {
			int largest = 0;
			for (int i = 1; i < remainders.length; i++) {
				if (remainders[i] > remainders[largest]) {
					largest = i;
				}
			}
			parts[largest]++;
			remainders[largest] = -1;
			left--;
		}
		return parts;
	}

}
