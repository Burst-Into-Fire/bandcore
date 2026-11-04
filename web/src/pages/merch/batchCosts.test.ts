import { describe, expect, it } from 'vitest';

import { lineCosts } from './batchCosts';

describe('lineCosts', () => {
  it('multiplies quantity by unit cost per item', () => {
    expect(
      lineCosts({
        costMode: 'PER_ITEM',
        totalCostCents: 0,
        items: [
          { quantity: 10, unitCostCents: 3000 },
          { quantity: 5, unitCostCents: 3200 },
        ],
      }),
    ).toEqual([30_000, 16_000]);
  });

  it('allocates a total by quantity so the lines add up exactly', () => {
    const lines = lineCosts({
      costMode: 'ALLOCATED',
      totalCostCents: 10_000,
      items: [1, 1, 1].map((quantity) => ({ quantity, unitCostCents: 0 })),
    });

    expect(lines).toEqual([3334, 3333, 3333]);
    expect(lines.reduce((sum, cost) => sum + cost, 0)).toBe(10_000);
    expect(
      lineCosts({
        costMode: 'ALLOCATED',
        totalCostCents: 46_000,
        items: [10, 5].map((quantity) => ({ quantity, unitCostCents: 0 })),
      }),
    ).toEqual([30_667, 15_333]);
  });

  it('gives nothing to lines without a valid quantity yet', () => {
    expect(
      lineCosts({
        costMode: 'ALLOCATED',
        totalCostCents: 1000,
        items: [
          { quantity: Number.NaN, unitCostCents: 0 },
          { quantity: 4, unitCostCents: 0 },
        ],
      }),
    ).toEqual([0, 1000]);
    expect(
      lineCosts({
        costMode: 'ALLOCATED',
        totalCostCents: 1000,
        items: [{ quantity: 0, unitCostCents: 0 }],
      }),
    ).toEqual([0]);
    expect(
      lineCosts({
        costMode: 'PER_ITEM',
        totalCostCents: 0,
        items: [{ quantity: Number.NaN, unitCostCents: 500 }],
      }),
    ).toEqual([0]);
  });
});
