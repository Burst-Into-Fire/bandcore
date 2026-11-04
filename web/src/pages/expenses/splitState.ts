import type { TFunction } from 'i18next';

import { formatPercent, type SplitEntry, type SplitMode } from '../../lib/splits';
import type { Expense, Member } from './types';

export interface SplitState {
  mode: SplitMode;
  entries: SplitEntry[];
  /** The text typed in each percentage field, by member id. */
  percentText: Record<string, string>;
}

export function memberName(t: TFunction, member: Member | undefined): string {
  if (!member) {
    return t('expenses.unknownMember');
  }
  return member.status === 'ACTIVE'
    ? member.displayName
    : t('expenses.leftBand', { name: member.displayName });
}

/** The editor's state for a new expense (equal, every active member) or an existing split. */
export function initialSplit(members: readonly Member[], expense?: Expense): SplitState {
  const sharing = new Map((expense?.split.shares ?? []).map((share) => [share.memberId, share]));
  const ids = new Set(members.filter((member) => member.status === 'ACTIVE').map((m) => m.id));
  sharing.forEach((_, memberId) => ids.add(memberId));
  const entries = [...ids].map((memberId): SplitEntry => {
    const share = sharing.get(memberId);
    return {
      memberId,
      selected: expense ? share !== undefined : true,
      amountCents: share?.amountCents ?? share?.shareCents ?? 0,
      basisPoints: share?.basisPoints ?? 0,
    };
  });
  const order = new Map(members.map((member, index) => [member.id, index]));
  entries.sort((a, b) => (order.get(a.memberId) ?? 0) - (order.get(b.memberId) ?? 0));
  const percentText: Record<string, string> = {};
  entries.forEach((entry) => {
    percentText[entry.memberId] = entry.basisPoints ? formatPercent(entry.basisPoints) : '';
  });
  return { mode: expense?.split.mode ?? 'EQUAL', entries, percentText };
}
