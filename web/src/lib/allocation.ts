/**
 * Splits an amount of cents into parts that always add up exactly to the total, with the same
 * algorithm as the API (`shared.money.Allocation`): every part gets the floor of its exact
 * share, and the cents left over go one each to the parts with the largest remainders, ties to
 * the lower index. Both are tested against contracts/money/allocation.json.
 *
 * Intermediate products use BigInt, so total * weight never loses precision.
 */

/** Percentages are given in basis points: 10000 is 100%. */
export const BASIS_POINTS = 10_000;

/** allocateEqually(10000, 3) -> [3334, 3333, 3333] */
export function allocateEqually(totalCents: number, parts: number): number[] {
  if (!Number.isInteger(parts) || parts < 1) {
    throw new RangeError('At least one part is needed');
  }
  return allocateByWeights(totalCents, Array<number>(parts).fill(1));
}

/** allocateByWeights(100, [3333, 3333, 3334]) -> [33, 33, 34]; weights are relative. */
export function allocateByWeights(totalCents: number, weights: readonly number[]): number[] {
  if (!Number.isSafeInteger(totalCents) || totalCents < 0) {
    throw new RangeError(
      `The total must be a non-negative integer number of cents, got ${totalCents}`,
    );
  }
  if (weights.length === 0) {
    throw new RangeError('At least one weight is needed');
  }
  if (weights.some((weight) => !Number.isSafeInteger(weight) || weight < 0)) {
    throw new RangeError('Weights must be non-negative integers');
  }
  const total = BigInt(totalCents);
  const big = weights.map((weight) => BigInt(weight));
  const weightSum = big.reduce((sum, weight) => sum + weight, 0n);
  if (weightSum === 0n) {
    throw new RangeError('At least one weight must be positive');
  }

  const parts = big.map((weight) => (total * weight) / weightSum);
  const remainders = big.map((weight) => (total * weight) % weightSum);
  let left = total - parts.reduce((sum, part) => sum + part, 0n);
  while (left > 0n) {
    let largest = 0;
    for (let i = 1; i < remainders.length; i++) {
      if (remainders[i]! > remainders[largest]!) {
        largest = i;
      }
    }
    parts[largest] = parts[largest]! + 1n;
    remainders[largest] = -1n;
    left -= 1n;
  }
  return parts.map(Number);
}
