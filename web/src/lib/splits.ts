import { allocateByWeights, allocateEqually, BASIS_POINTS } from './allocation';

/**
 * The split editor's arithmetic, mirroring the API (finance.expense.SplitCalculator): members are
 * ordered by id as text before allocating, so the preview shows exactly the cents the API saves.
 */

export type SplitMode = 'EQUAL' | 'CUSTOM_AMOUNTS' | 'CUSTOM_PERCENTAGES';

export interface SplitEntry {
  memberId: string;
  /** EQUAL: whether the member shares the expense. */
  selected: boolean;
  /** CUSTOM_AMOUNTS: cents. */
  amountCents: number;
  /** CUSTOM_PERCENTAGES: basis points (10000 = 100%), or null while the text is not a number. */
  basisPoints: number | null;
}

export interface SplitPreview {
  /** Cents per member id, for the members who share the expense. */
  shares: Map<string, number>;
  /** CUSTOM_AMOUNTS: total minus the amounts given (negative when they go over). */
  remainingCents: number;
  /** CUSTOM_PERCENTAGES: 10000 minus the percentages given. */
  remainingBasisPoints: number;
  /** Whether the API will accept the split: someone shares it and everything adds up. */
  complete: boolean;
}

/** The API's member order: uuid text, compared character by character. */
export function byMemberId(a: { memberId: string }, b: { memberId: string }): number {
  return a.memberId < b.memberId ? -1 : a.memberId > b.memberId ? 1 : 0;
}

/** Members with a part in the expense, in the API's order. */
function participants(mode: SplitMode, entries: readonly SplitEntry[]): SplitEntry[] {
  return entries
    .filter((entry) => {
      if (mode === 'EQUAL') return entry.selected;
      if (mode === 'CUSTOM_AMOUNTS') return entry.amountCents > 0;
      return (entry.basisPoints ?? 0) > 0;
    })
    .sort(byMemberId);
}

export function previewSplit(
  mode: SplitMode,
  totalCents: number,
  entries: readonly SplitEntry[],
): SplitPreview {
  const sharing = participants(mode, entries);
  const shares = new Map<string, number>();
  if (mode === 'EQUAL') {
    if (sharing.length > 0) {
      allocateEqually(totalCents, sharing.length).forEach((cents, i) =>
        shares.set(sharing[i]!.memberId, cents),
      );
    }
    return {
      shares,
      remainingCents: 0,
      remainingBasisPoints: 0,
      complete: sharing.length > 0 && totalCents > 0,
    };
  }
  if (mode === 'CUSTOM_AMOUNTS') {
    const given = sharing.reduce((sum, entry) => sum + entry.amountCents, 0);
    sharing.forEach((entry) => shares.set(entry.memberId, entry.amountCents));
    return {
      shares,
      remainingCents: totalCents - given,
      remainingBasisPoints: 0,
      complete: sharing.length > 0 && totalCents > 0 && given === totalCents,
    };
  }
  const invalid = entries.some((entry) => entry.basisPoints === null);
  const given = sharing.reduce((sum, entry) => sum + (entry.basisPoints ?? 0), 0);
  const complete = !invalid && sharing.length > 0 && totalCents > 0 && given === BASIS_POINTS;
  if (complete) {
    allocateByWeights(
      totalCents,
      sharing.map((entry) => entry.basisPoints ?? 0),
    ).forEach((cents, i) => shares.set(sharing[i]!.memberId, cents));
  }
  return { shares, remainingCents: 0, remainingBasisPoints: BASIS_POINTS - given, complete };
}

/** The request body's split, in the API's member order. */
export function splitBody(mode: SplitMode, entries: readonly SplitEntry[]) {
  const sharing = participants(mode, entries);
  if (mode === 'EQUAL') {
    return { mode, shares: sharing.map((entry) => ({ memberId: entry.memberId })) };
  }
  if (mode === 'CUSTOM_AMOUNTS') {
    return {
      mode,
      shares: sharing.map((entry) => ({
        memberId: entry.memberId,
        amountCents: entry.amountCents,
      })),
    };
  }
  return {
    mode,
    shares: sharing.map((entry) => ({
      memberId: entry.memberId,
      basisPoints: entry.basisPoints ?? 0,
    })),
  };
}

const PERCENT = /^(?<whole>\d{1,3})(?:,(?<fraction>\d{1,2}))?$/;

/** "33,33" -> 3333 basis points; "" -> 0; anything else (or above 100%) -> null. No floats. */
export function parsePercent(text: string): number | null {
  const trimmed = text.replace('%', '').trim();
  if (trimmed === '') {
    return 0;
  }
  const match = PERCENT.exec(trimmed);
  if (!match?.groups) {
    return null;
  }
  const whole = Number(match.groups.whole ?? '0');
  const fraction = Number((match.groups.fraction ?? '').padEnd(2, '0'));
  const basisPoints = whole * 100 + fraction;
  return basisPoints > BASIS_POINTS ? null : basisPoints;
}

/** 3333 -> "33,33"; 5000 -> "50"; negative values keep their sign. */
export function formatPercent(basisPoints: number): string {
  const sign = basisPoints < 0 ? '-' : '';
  const absolute = Math.abs(basisPoints);
  const whole = Math.trunc(absolute / 100);
  const fraction = absolute % 100;
  return fraction === 0
    ? `${sign}${whole}`
    : `${sign}${whole},${String(fraction).padStart(2, '0').replace(/0$/, '')}`;
}
