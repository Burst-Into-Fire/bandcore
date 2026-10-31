/**
 * Money is always an integer number of cents (ADR-0006). These helpers never use floating
 * point: formatting goes through a decimal string and parsing works on the digits.
 */

export type Currency = 'BRL' | 'USD';

const LOCALE = 'pt-BR';

const currencyFormatters = new Map<Currency, Intl.NumberFormat>();

const plainFormatter = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

function currencyFormatter(currency: Currency): Intl.NumberFormat {
  let formatter = currencyFormatters.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat(LOCALE, { style: 'currency', currency });
    currencyFormatters.set(currency, formatter);
  }
  return formatter;
}

function assertCents(cents: number): void {
  if (!Number.isSafeInteger(cents)) {
    throw new RangeError(`Money must be an integer number of cents, got ${cents}`);
  }
}

/** 350000 -> "3500.00", exact, so Intl never sees a binary fraction. */
function toDecimalString(cents: number): `${number}` {
  const sign = cents < 0 ? '-' : '';
  const digits = Math.abs(cents).toString().padStart(3, '0');
  return `${sign}${digits.slice(0, -2)}.${digits.slice(-2)}` as `${number}`;
}

/** formatMoney(350000) -> "R$ 3.500,00" (with a non-breaking space, as Intl does). */
export function formatMoney(cents: number, currency: Currency = 'BRL'): string {
  assertCents(cents);
  return currencyFormatter(currency).format(toDecimalString(cents));
}

/** formatAmount(350000) -> "3.500,00", without the currency symbol. */
export function formatAmount(cents: number): string {
  assertCents(cents);
  return plainFormatter.format(toDecimalString(cents));
}

const AMOUNT = /^(?<whole>\d{1,3}(?:\.\d{3})*|\d+)(?:,(?<fraction>\d{1,2}))?$/;

/**
 * Parses a non-negative pt-BR amount into cents: "3.500,00" -> 350000, "0,01" -> 1,
 * "R$ 12" -> 1200. Returns null for anything else (negative numbers, more than two
 * decimals, misplaced thousands separators, values beyond the safe integer range).
 */
export function parseMoney(input: string): number | null {
  const text = input
    .replace(/^\s*R\$/, '')
    .replace(/\s/g, '') // \s includes the non-breaking space Intl uses
    .trim();
  const match = AMOUNT.exec(text);
  if (!match?.groups) {
    return null;
  }
  const whole = (match.groups.whole ?? '').replaceAll('.', '');
  const fraction = (match.groups.fraction ?? '').padEnd(2, '0');
  const cents = BigInt(whole) * 100n + BigInt(fraction);
  if (cents > BigInt(Number.MAX_SAFE_INTEGER)) {
    return null;
  }
  return Number(cents);
}
