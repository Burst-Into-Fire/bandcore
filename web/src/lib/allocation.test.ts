import { describe, expect, it } from 'vitest';

import { allocateByWeights, allocateEqually, BASIS_POINTS } from './allocation';

interface Vectors {
  equal: { name: string; totalCents: number; parts: number; expected: number[] }[];
  byWeights: { name: string; totalCents: number; weights: number[]; expected: number[] }[];
}

// The vectors live outside the web app, next to the API tests that use them too. A glob keeps
// the path out of the type checker, which only sees web/ when the Docker image is built.
const files = import.meta.glob<Vectors>('../../../contracts/money/allocation.json', {
  eager: true,
  import: 'default',
});
const vectors = Object.values(files)[0];

describe('allocation shared vectors', () => {
  it('finds the vectors file', () => {
    expect(vectors).toBeDefined();
  });

  describe.each(vectors?.equal ?? [])('equal: $name', ({ totalCents, parts, expected }) => {
    it('matches the API', () => {
      expect(allocateEqually(totalCents, parts)).toEqual(expected);
    });
  });

  describe.each(vectors?.byWeights ?? [])(
    'byWeights: $name',
    ({ totalCents, weights, expected }) => {
      it('matches the API', () => {
        expect(allocateByWeights(totalCents, weights)).toEqual(expected);
      });
    },
  );
});

describe('allocation', () => {
  it('always sums to the total', () => {
    for (let total = 0; total < 500; total += 7) {
      for (let parts = 1; parts <= 7; parts++) {
        const result = allocateEqually(total, parts);
        expect(result.reduce((sum, part) => sum + part, 0)).toBe(total);
        expect(Math.max(...result) - Math.min(...result)).toBeLessThanOrEqual(1);
      }
    }
  });

  it('keeps large amounts exact', () => {
    const total = Number.MAX_SAFE_INTEGER - 1;
    const result = allocateByWeights(total, [3333, 3333, 3334]);
    expect(result.reduce((sum, part) => sum + part, 0)).toBe(total);
  });

  it('accepts a hundred percent in basis points', () => {
    expect(allocateByWeights(100, [BASIS_POINTS])).toEqual([100]);
  });

  it('rejects invalid input', () => {
    expect(() => allocateEqually(100, 0)).toThrow(RangeError);
    expect(() => allocateEqually(-1, 2)).toThrow(RangeError);
    expect(() => allocateEqually(1.5, 2)).toThrow(RangeError);
    expect(() => allocateByWeights(100, [])).toThrow(RangeError);
    expect(() => allocateByWeights(100, [0, 0])).toThrow(RangeError);
    expect(() => allocateByWeights(100, [1, -1])).toThrow(RangeError);
  });
});
