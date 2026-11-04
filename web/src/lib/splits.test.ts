import { describe, expect, it } from 'vitest';

import {
  byMemberId,
  formatPercent,
  parsePercent,
  previewSplit,
  splitBody,
  type SplitEntry,
} from './splits';

interface Vectors {
  equal: { name: string; totalCents: number; parts: number; expected: number[] }[];
  byWeights: { name: string; totalCents: number; weights: number[]; expected: number[] }[];
}

// The same vectors as the API tests (see allocation.test.ts for why this is a glob).
const vectors = Object.values(
  import.meta.glob<Vectors>('../../../contracts/money/allocation.json', {
    eager: true,
    import: 'default',
  }),
)[0];

/** Member ids that sort in creation order, like UUIDv7. */
const memberId = (i: number) => `0190a000-0000-7000-8000-${String(i).padStart(12, '0')}`;

function entry(i: number, values: Partial<SplitEntry> = {}): SplitEntry {
  return { memberId: memberId(i), selected: true, amountCents: 0, basisPoints: 0, ...values };
}

describe('split editor and the API agree on every cent', () => {
  it('finds the shared vectors', () => {
    expect(vectors).toBeDefined();
  });

  describe.each(vectors?.equal.filter((vector) => vector.totalCents > 0) ?? [])(
    'equal: $name',
    ({ totalCents, parts, expected }) => {
      it('previews the API shares', () => {
        // Given in reverse order: the preview sorts by member id like the API.
        const entries = Array.from({ length: parts }, (_, i) => entry(i)).reverse();
        const preview = previewSplit('EQUAL', totalCents, entries);
        expect(preview.complete).toBe(true);
        expect(Array.from({ length: parts }, (_, i) => preview.shares.get(memberId(i)))).toEqual(
          expected,
        );
      });
    },
  );

  describe.each(
    vectors?.byWeights.filter(
      (vector) =>
        vector.totalCents > 0 &&
        vector.weights.reduce((sum, weight) => sum + weight, 0) === 10_000 &&
        vector.weights.every((weight) => weight > 0),
    ) ?? [],
  )('percentages: $name', ({ totalCents, weights, expected }) => {
    it('previews the API shares', () => {
      const entries = weights.map((basisPoints, i) => entry(i, { basisPoints })).reverse();
      const preview = previewSplit('CUSTOM_PERCENTAGES', totalCents, entries);
      expect(preview.complete).toBe(true);
      expect(weights.map((_, i) => preview.shares.get(memberId(i)))).toEqual(expected);
    });
  });
});

describe('previewSplit', () => {
  it('counts what is left of custom amounts and completes only at zero', () => {
    const entries = [entry(0, { amountCents: 6000 }), entry(1, { amountCents: 3000 })];
    expect(previewSplit('CUSTOM_AMOUNTS', 10_000, entries)).toMatchObject({
      remainingCents: 1000,
      complete: false,
    });
    entries[1] = entry(1, { amountCents: 5000 });
    expect(previewSplit('CUSTOM_AMOUNTS', 10_000, entries)).toMatchObject({
      remainingCents: -1000,
      complete: false,
    });
    entries[1] = entry(1, { amountCents: 4000 });
    expect(previewSplit('CUSTOM_AMOUNTS', 10_000, entries)).toMatchObject({
      remainingCents: 0,
      complete: true,
    });
  });

  it('needs percentages that are numbers and add up to 100', () => {
    expect(
      previewSplit('CUSTOM_PERCENTAGES', 100, [
        entry(0, { basisPoints: 5000 }),
        entry(1, { basisPoints: null }),
      ]).complete,
    ).toBe(false);
    expect(
      previewSplit('CUSTOM_PERCENTAGES', 100, [entry(0, { basisPoints: 9000 })])
        .remainingBasisPoints,
    ).toBe(1000);
  });

  it('needs someone to share an equal split', () => {
    expect(previewSplit('EQUAL', 100, [entry(0, { selected: false })]).complete).toBe(false);
  });
});

describe('splitBody', () => {
  it('sends only the members with a part, in the API order', () => {
    const entries = [
      entry(2, { amountCents: 700 }),
      entry(1, { amountCents: 0 }),
      entry(0, { amountCents: 300 }),
    ];
    expect(splitBody('CUSTOM_AMOUNTS', entries)).toEqual({
      mode: 'CUSTOM_AMOUNTS',
      shares: [
        { memberId: memberId(0), amountCents: 300 },
        { memberId: memberId(2), amountCents: 700 },
      ],
    });
    expect(splitBody('EQUAL', [entry(1), entry(0, { selected: false })])).toEqual({
      mode: 'EQUAL',
      shares: [{ memberId: memberId(1) }],
    });
  });

  it('orders ids as text, not as numbers', () => {
    const ids = [{ memberId: 'f0' }, { memberId: '0a' }, { memberId: '1b' }];
    expect([...ids].sort(byMemberId).map((id) => id.memberId)).toEqual(['0a', '1b', 'f0']);
  });
});

describe('percent text', () => {
  it.each([
    ['33,33', 3333],
    ['50', 5000],
    ['0,5', 50],
    ['100', 10_000],
    ['12,5%', 1250],
    ['', 0],
    ['100,01', null],
    ['1.5', null],
    ['abc', null],
    ['-5', null],
  ])('parses %j', (text, expected) => {
    expect(parsePercent(text)).toBe(expected);
  });

  it.each([
    [3333, '33,33'],
    [5000, '50'],
    [50, '0,5'],
    [1250, '12,5'],
    [-1000, '-10'],
  ])('formats %d', (basisPoints, expected) => {
    expect(formatPercent(basisPoints)).toBe(expected);
  });
});
