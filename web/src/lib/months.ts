/** "2026-11" moved by some months: shiftMonth("2026-01", -1) is "2025-12". */
export function shiftMonth(month: string, delta: number): string {
  const [year, value] = month.split('-').map(Number);
  const index = (year ?? 1970) * 12 + ((value ?? 1) - 1) + delta;
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}`;
}
