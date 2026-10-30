import { describe, expect, it } from 'vitest';

import { formatDate, formatDateTime, toLocalDate } from './dates';

// 02:00 UTC on the 15th is still the 14th in São Paulo (UTC-3).
const LATE_NIGHT_IN_BRAZIL = '2026-01-15T02:00:00Z';

describe('dates in the band time zone', () => {
  it('uses the band time zone, not UTC', () => {
    expect(toLocalDate(LATE_NIGHT_IN_BRAZIL, 'America/Sao_Paulo')).toBe('2026-01-14');
    expect(toLocalDate(LATE_NIGHT_IN_BRAZIL, 'UTC')).toBe('2026-01-15');
  });

  it('formats dates in pt-BR', () => {
    expect(formatDate(LATE_NIGHT_IN_BRAZIL)).toBe('14/01/2026');
    expect(formatDateTime(LATE_NIGHT_IN_BRAZIL)).toMatch(/^14\/01\/2026,? 23:00$/);
  });

  it('rejects invalid dates', () => {
    expect(() => formatDate('not a date')).toThrow(RangeError);
  });
});
