/**
 * Dates are shown in the band's time zone, never the browser's, so every member sees the same
 * day for the same expense or sale.
 */

export const DEFAULT_TIME_ZONE = 'America/Sao_Paulo';

const LOCALE = 'pt-BR';

type DateInput = string | Date;

function toDate(value: DateInput): Date {
  const date = typeof value === 'string' ? new Date(value) : value;
  if (Number.isNaN(date.getTime())) {
    throw new RangeError(`Invalid date: ${String(value)}`);
  }
  return date;
}

/** "2026-01-15T02:00:00Z" in America/Sao_Paulo -> "14/01/2026". */
export function formatDate(value: DateInput, timeZone: string = DEFAULT_TIME_ZONE): string {
  return new Intl.DateTimeFormat(LOCALE, { timeZone, dateStyle: 'short' }).format(toDate(value));
}

/** "2026-01-15T02:00:00Z" in America/Sao_Paulo -> "14/01/2026 23:00". */
export function formatDateTime(value: DateInput, timeZone: string = DEFAULT_TIME_ZONE): string {
  return new Intl.DateTimeFormat(LOCALE, {
    timeZone,
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(toDate(value));
}

/** Calendar day of an instant in the band's time zone, as ISO "YYYY-MM-DD". */
export function toLocalDate(value: DateInput, timeZone: string = DEFAULT_TIME_ZONE): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(toDate(value));
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((candidate) => candidate.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}
