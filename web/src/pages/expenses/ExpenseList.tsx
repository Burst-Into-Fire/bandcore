import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { api } from '../../api/client';
import { unwrap } from '../../api/problem';
import { primaryButton, secondaryButton, textLink } from '../../components/buttons';
import { StatusBadge } from '../../components/Card';
import { Confirm } from '../../components/Confirm';
import { FormAlert } from '../../components/FormAlert';
import { SelectField } from '../../components/SelectField';
import { TextField } from '../../components/TextField';
import { ChevronDownIcon } from '../../components/icons';
import { formatMoney } from '../../lib/money';
import { errorMessage } from '../errorMessage';
import { memberName } from './splitState';
import {
  categoryLabel,
  dayMonth,
  groupByMonth,
  monthLabel,
  type Category,
  type Expense,
  type Member,
} from './types';

interface ListContext {
  members: ReadonlyMap<string, Member>;
  categories: ReadonlyMap<string, Category>;
  ownMemberId: string | undefined;
  today: string;
  onEdit: (expense: Expense) => void;
}

/** Expenses grouped by month: description, category and payer, amount on the right, status. */
export function ExpenseList({
  expenses,
  members,
  categories,
  ownMemberId,
  today,
  onEdit,
}: {
  expenses: readonly Expense[];
  members: readonly Member[];
  categories: readonly Category[];
  ownMemberId: string | undefined;
  today: string;
  onEdit: (expense: Expense) => void;
}) {
  const context: ListContext = {
    members: new Map(members.map((member) => [member.id, member])),
    categories: new Map(categories.map((category) => [category.id, category])),
    ownMemberId,
    today,
    onEdit,
  };
  return (
    <div className="flex flex-col gap-6">
      {groupByMonth(expenses).map((group) => (
        <section key={group.month} aria-label={monthLabel(`${group.month}-01`)}>
          <h2 className="text-card-title text-muted mb-2 font-semibold">
            {monthLabel(`${group.month}-01`)}
          </h2>
          <ul className="rounded-card border-border bg-surface divide-border divide-y border">
            {group.items.map((expense) => (
              <ExpenseRow key={expense.id} expense={expense} context={context} />
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

type Action = 'pay' | 'delete' | 'revert' | null;

/** Action names (not UI text). */
const PAY = 'pay';
const DELETE = 'delete';
const REVERT = 'revert';

function ExpenseRow({ expense, context }: { expense: Expense; context: ListContext }) {
  const { t } = useTranslation();
  const detailsId = useId();
  const [open, setOpen] = useState(false);
  const [action, setAction] = useState<Action>(null);
  const queryClient = useQueryClient();
  const category = context.categories.get(expense.categoryId);
  const payer = expense.paidByMemberId ? context.members.get(expense.paidByMemberId) : undefined;
  const sharing = expense.split.shares.length;

  const change = useMutation({
    mutationFn: async (kind: 'delete' | 'revert') => {
      const path = { params: { path: { expenseId: expense.id } } };
      if (kind === 'delete') {
        return unwrap(
          await api.DELETE('/expenses/{expenseId}', {
            params: { path: { expenseId: expense.id }, query: { version: expense.version } },
            parseAs: 'text',
          }),
        );
      }
      return unwrap(
        await api.POST('/expenses/{expenseId}/revert-payment', {
          ...path,
          body: { version: expense.version },
        }),
      );
    },
    onSuccess: async () => {
      setAction(null);
      await queryClient.invalidateQueries({ queryKey: ['expenses'] });
    },
  });

  return (
    <li className="flex flex-col">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={detailsId}
        onClick={() => setOpen(!open)}
        className="hover:bg-surface-raised flex w-full items-center justify-between gap-3 px-4 py-3 text-left"
      >
        <span className="flex min-w-0 flex-col">
          <span className="truncate font-medium">{expense.description}</span>
          <span className="text-caption text-muted truncate">
            {[
              category ? categoryLabel(t, category) : null,
              payer
                ? t('expenses.paidBy', { name: memberName(t, payer) })
                : t('expenses.sharedBy', { count: sharing }),
            ]
              .filter(Boolean)
              .join(' · ')}
          </span>
        </span>
        <span className="flex shrink-0 items-center gap-2">
          <span className="flex flex-col items-end gap-1">
            <span className="text-right font-semibold tabular-nums">
              {formatMoney(expense.amountCents, expense.currency)}
            </span>
            <ExpenseBadge expense={expense} />
          </span>
          <ChevronDownIcon
            className={`text-muted transition-transform ${open ? 'rotate-180' : ''}`}
          />
        </span>
      </button>
      {open ? (
        <div id={detailsId} className="bg-surface-raised flex flex-col gap-3 px-4 py-3">
          <div className="flex flex-col gap-1">
            <p className="text-card-title text-muted">
              {t('expenses.shares')} · {t('expenses.sharedBy', { count: sharing })}
            </p>
            <ul className="flex flex-col">
              {expense.split.shares.map((share) => (
                <li key={share.memberId} className="flex justify-between gap-3 py-1">
                  <span className="truncate">
                    {memberName(t, context.members.get(share.memberId))}
                  </span>
                  <span className="tabular-nums">
                    {formatMoney(share.shareCents, expense.currency)}
                  </span>
                </li>
              ))}
            </ul>
          </div>
          {expense.notes ? (
            <p>
              <span className="text-card-title text-muted">{t('expenses.notes')}: </span>
              {expense.notes}
            </p>
          ) : null}
          {action === 'pay' ? (
            <PayForm expense={expense} context={context} onDone={() => setAction(null)} />
          ) : null}
          {action === 'delete' ? (
            <Confirm
              question={
                expense.status === 'PAID'
                  ? t('expenses.confirmDeletePaid')
                  : t('expenses.confirmDeletePlanned')
              }
              pending={change.isPending}
              onConfirm={() => change.mutate(DELETE)}
              onCancel={() => setAction(null)}
            />
          ) : null}
          {action === 'revert' ? (
            <Confirm
              question={t('expenses.confirmRevert')}
              pending={change.isPending}
              onConfirm={() => change.mutate(REVERT)}
              onCancel={() => setAction(null)}
            />
          ) : null}
          {change.isError ? <FormAlert message={errorMessage(t, change.error)} /> : null}
          {action === null ? (
            <div className="flex flex-wrap gap-x-4 gap-y-2">
              {expense.access.pay ? (
                <button type="button" className={textLink} onClick={() => setAction(PAY)}>
                  {t('expenses.pay')}
                </button>
              ) : null}
              {expense.access.edit ? (
                <button type="button" className={textLink} onClick={() => context.onEdit(expense)}>
                  {t('expenses.edit')}
                </button>
              ) : null}
              {expense.access.revertPayment ? (
                <button type="button" className={textLink} onClick={() => setAction(REVERT)}>
                  {t('expenses.revert')}
                </button>
              ) : null}
              {expense.access.delete ? (
                <button type="button" className={textLink} onClick={() => setAction(DELETE)}>
                  {t('expenses.delete')}
                </button>
              ) : null}
            </div>
          ) : null}
        </div>
      ) : null}
    </li>
  );
}

/** "Pago" in muted text; "Previsto dd/MM" in coral; overdue ones say so. */
export function ExpenseBadge({ expense }: { expense: Expense }) {
  const { t } = useTranslation();
  if (expense.status === 'PAID') {
    return <StatusBadge tone="neutral">{t('expenses.paid')}</StatusBadge>;
  }
  const date = dayMonth(expense.expenseDate);
  return expense.overdue ? (
    <StatusBadge tone="warning">{t('expenses.overdue', { date })}</StatusBadge>
  ) : (
    <StatusBadge tone="accent">{t('expenses.planned', { date })}</StatusBadge>
  );
}

/**
 * Who paid and when. Members who may change the expense choose anyone; the others can only
 * record that they paid it themselves.
 */
function PayForm({
  expense,
  context,
  onDone,
}: {
  expense: Expense;
  context: ListContext;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [payer, setPayer] = useState(context.ownMemberId ?? '');
  const [paidOn, setPaidOn] = useState(context.today);
  const payers = expense.access.edit
    ? [...context.members.values()].filter((member) => member.status === 'ACTIVE')
    : [...context.members.values()].filter((member) => member.id === context.ownMemberId);
  const pay = useMutation({
    mutationFn: async () =>
      unwrap(
        await api.POST('/expenses/{expenseId}/pay', {
          params: { path: { expenseId: expense.id } },
          body: {
            ...(payer ? { payerMemberId: payer } : {}),
            paidOn,
            version: expense.version,
          },
        }),
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['expenses'] });
      onDone();
    },
  });

  return (
    <form
      noValidate
      aria-label={t('expenses.pay')}
      className="flex flex-col gap-3"
      onSubmit={(event) => {
        event.preventDefault();
        pay.mutate();
      }}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <SelectField
          label={t('expenses.payer')}
          options={[
            ...(payer ? [] : [{ value: '', label: t('expenses.choose') }]),
            ...payers.map((member) => ({ value: member.id, label: memberName(t, member) })),
          ]}
          value={payer}
          onChange={(event) => setPayer(event.target.value)}
        />
        <TextField
          type="date"
          label={t('expenses.paidOn')}
          value={paidOn}
          onChange={(event) => setPaidOn(event.target.value)}
        />
      </div>
      {pay.isError ? <FormAlert message={errorMessage(t, pay.error)} /> : null}
      <div className="flex flex-wrap gap-2">
        <button type="submit" className={primaryButton} disabled={pay.isPending || !payer}>
          {pay.isPending ? t('expenses.saving') : t('expenses.payConfirm')}
        </button>
        <button type="button" className={secondaryButton} onClick={onDone}>
          {t('expenses.cancel')}
        </button>
      </div>
    </form>
  );
}
