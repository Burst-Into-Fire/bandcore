import { describe, expect, it } from 'vitest';

import { formatAmount, formatMoney, parseMoney } from './money';

const NBSP = '\u00a0';

describe('formatMoney', () => {
  it('formats cents as BRL', () => {
    expect(formatMoney(350000)).toBe(`R$${NBSP}3.500,00`);
    expect(formatMoney(1)).toBe(`R$${NBSP}0,01`);
    expect(formatMoney(0)).toBe(`R$${NBSP}0,00`);
    expect(formatMoney(-1990)).toBe(`-R$${NBSP}19,90`);
  });

  it('formats large amounts exactly', () => {
    expect(formatMoney(Number.MAX_SAFE_INTEGER)).toBe(`R$${NBSP}90.071.992.547.409,91`);
  });

  it('formats USD', () => {
    expect(formatMoney(1050, 'USD')).toBe(`US$${NBSP}10,50`);
  });

  it('rejects non-integer cents', () => {
    expect(() => formatMoney(10.5)).toThrow(RangeError);
    expect(() => formatMoney(Number.NaN)).toThrow(RangeError);
  });
});

describe('formatAmount', () => {
  it('formats without the currency symbol', () => {
    expect(formatAmount(350000)).toBe('3.500,00');
    expect(formatAmount(5)).toBe('0,05');
  });
});

describe('parseMoney', () => {
  it.each([
    ['3.500,00', 350000],
    ['0,01', 1],
    ['3500', 350000],
    ['3500,5', 350050],
    ['1.234.567,89', 123456789],
    [`R$${NBSP}3.500,00`, 350000],
    ['  R$ 12 ', 1200],
    ['0', 0],
  ])('parses %j as %i cents', (input, cents) => {
    expect(parseMoney(input)).toBe(cents);
  });

  it.each([
    '',
    'abc',
    '-1,00',
    '1,234',
    '3.50',
    '35.00,00',
    '1.2345',
    '1,',
    ',50',
    '1e3',
    '99999999999999999999',
  ])('rejects %j', (input) => {
    expect(parseMoney(input)).toBeNull();
  });

  it('round-trips with formatAmount', () => {
    for (const cents of [0, 1, 99, 100, 101, 350000, 123456789]) {
      expect(parseMoney(formatAmount(cents))).toBe(cents);
    }
  });
});
