import { describe, expect, it } from 'vitest';

import { shiftMonth } from './months';

describe('shiftMonth', () => {
  it.each([
    ['2026-11', 1, '2026-12'],
    ['2026-12', 1, '2027-01'],
    ['2026-01', -1, '2025-12'],
    ['2026-03', -14, '2025-01'],
    ['2026-07', 0, '2026-07'],
  ])('moves %s by %d to %s', (month, delta, expected) => {
    expect(shiftMonth(month, delta)).toBe(expected);
  });
});
