import { useId } from 'react';
import { useTranslation } from 'react-i18next';

import { MoneyInput } from '../../components/MoneyInput';
import { formatMoney, type Currency } from '../../lib/money';
import {
  formatPercent,
  parsePercent,
  previewSplit,
  type SplitEntry,
  type SplitMode,
  type SplitPreview,
} from '../../lib/splits';
import { memberName, type SplitState } from './splitState';
import type { Member } from './types';

const MODES: readonly SplitMode[] = ['EQUAL', 'CUSTOM_AMOUNTS', 'CUSTOM_PERCENTAGES'];

/**
 * Who shares the expense and how: equal parts, an amount each or a percentage each. Shows each
 * member's cents as the API will save them, and what is still missing; the form saves only when
 * the preview is complete.
 */
export function SplitEditor({
  value,
  onChange,
  members,
  totalCents,
  currency,
}: {
  value: SplitState;
  onChange: (next: SplitState) => void;
  members: readonly Member[];
  totalCents: number;
  currency: Currency;
}) {
  const { t } = useTranslation();
  const groupId = useId();
  const preview = previewSplit(value.mode, totalCents, value.entries);
  const names = new Map(members.map((member) => [member.id, member]));

  const update = (memberId: string, change: Partial<SplitEntry>) =>
    onChange({
      ...value,
      entries: value.entries.map((entry) =>
        entry.memberId === memberId ? { ...entry, ...change } : entry,
      ),
    });

  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="text-card-title text-muted mb-1">{t('split.title')}</legend>
      <div
        role="radiogroup"
        aria-label={t('split.mode')}
        className="border-border grid grid-cols-3 overflow-hidden rounded-lg border"
      >
        {MODES.map((mode) => (
          <label
            key={mode}
            className={`cursor-pointer px-2 py-2 text-center text-sm font-semibold has-[:focus-visible]:outline-2 ${
              value.mode === mode ? 'bg-accent text-accent-foreground' : 'text-muted'
            }`}
          >
            <input
              type="radio"
              name={`${groupId}-mode`}
              value={mode}
              checked={value.mode === mode}
              onChange={() => onChange({ ...value, mode })}
              className="sr-only"
            />
            {t(`split.${mode}`)}
          </label>
        ))}
      </div>
      <ul className="divide-border flex flex-col divide-y">
        {value.entries.map((entry) => {
          const member = names.get(entry.memberId);
          const name = memberName(t, member);
          const share = preview.shares.get(entry.memberId);
          return (
            <li key={entry.memberId} className="flex items-center justify-between gap-3 py-2">
              {value.mode === 'EQUAL' ? (
                <label className="flex min-w-0 items-center gap-2">
                  <input
                    type="checkbox"
                    checked={entry.selected}
                    onChange={(event) => update(entry.memberId, { selected: event.target.checked })}
                    className="accent-accent size-5"
                  />
                  <span className="truncate">{name}</span>
                </label>
              ) : (
                <span className="min-w-0 truncate">{name}</span>
              )}
              {value.mode === 'CUSTOM_AMOUNTS' ? (
                <div className="w-36 shrink-0">
                  <MoneyInput
                    label={t('split.amountOf', { name })}
                    labelHidden
                    className="w-full"
                    value={entry.amountCents}
                    onChange={(cents) => update(entry.memberId, { amountCents: cents })}
                  />
                </div>
              ) : null}
              {value.mode === 'CUSTOM_PERCENTAGES' ? (
                <PercentInput
                  label={t('split.percentOf', { name })}
                  invalidMessage={t('split.invalidPercent')}
                  text={value.percentText[entry.memberId] ?? ''}
                  onChange={(text) =>
                    onChange({
                      ...value,
                      percentText: { ...value.percentText, [entry.memberId]: text },
                      entries: value.entries.map((candidate) =>
                        candidate.memberId === entry.memberId
                          ? { ...candidate, basisPoints: parsePercent(text) }
                          : candidate,
                      ),
                    })
                  }
                  invalid={entry.basisPoints === null}
                />
              ) : null}
              {value.mode !== 'CUSTOM_AMOUNTS' ? (
                <span className="text-muted w-28 shrink-0 text-right tabular-nums">
                  {share !== undefined ? formatMoney(share, currency) : '—'}
                </span>
              ) : null}
            </li>
          );
        })}
      </ul>
      <SplitStatus
        preview={preview}
        mode={value.mode}
        totalCents={totalCents}
        currency={currency}
      />
    </fieldset>
  );
}

function SplitStatus({
  preview,
  mode,
  totalCents,
  currency,
}: {
  preview: SplitPreview;
  mode: SplitMode;
  totalCents: number;
  currency: Currency;
}) {
  const { t } = useTranslation();
  let message: string;
  let tone = 'text-warning';
  if (totalCents <= 0) {
    message = t('split.enterAmount');
    tone = 'text-muted';
  } else if (preview.complete) {
    message = t('split.complete');
    tone = 'text-success';
  } else if (mode === 'CUSTOM_AMOUNTS') {
    message =
      preview.remainingCents >= 0
        ? t('split.missingCents', { amount: formatMoney(preview.remainingCents, currency) })
        : t('split.overCents', { amount: formatMoney(-preview.remainingCents, currency) });
  } else if (mode === 'CUSTOM_PERCENTAGES' && preview.remainingBasisPoints !== 0) {
    message =
      preview.remainingBasisPoints > 0
        ? t('split.missingPercent', { percent: formatPercent(preview.remainingBasisPoints) })
        : t('split.overPercent', { percent: formatPercent(-preview.remainingBasisPoints) });
  } else {
    message = t('split.chooseMembers');
  }
  return (
    <p role="status" aria-live="polite" className={`font-semibold ${tone}`}>
      {message}
    </p>
  );
}

function PercentInput({
  label,
  text,
  onChange,
  invalid,
  invalidMessage,
}: {
  label: string;
  text: string;
  onChange: (text: string) => void;
  invalid: boolean;
  invalidMessage: string;
}) {
  const id = useId();
  return (
    <div className="flex w-24 shrink-0 flex-col items-end gap-1">
      <label htmlFor={id} className="sr-only">
        {label}
      </label>
      <div className="flex items-center gap-1">
        <input
          id={id}
          type="text"
          inputMode="decimal"
          autoComplete="off"
          value={text}
          aria-invalid={invalid || undefined}
          aria-describedby={invalid ? `${id}-error` : undefined}
          onChange={(event) => onChange(event.target.value)}
          className="border-border bg-surface-raised focus:border-accent aria-invalid:border-warning w-16 rounded-lg border px-2 py-2 text-right tabular-nums focus:outline-none"
        />
        <span aria-hidden="true" className="text-muted">
          %
        </span>
      </div>
      {invalid ? (
        <p id={`${id}-error`} className="text-caption text-warning text-right">
          {invalidMessage}
        </p>
      ) : null}
    </div>
  );
}
