import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';

import { api } from '../../api/client';
import { unwrap } from '../../api/problem';
import { useBandAccess } from '../../auth/useBandAccess';
import { primaryButton, secondaryButton, textLink } from '../../components/buttons';
import { PageHeader } from '../../components/PageHeader';
import { SelectField } from '../../components/SelectField';
import { EmptyState, ErrorState, LoadingState } from '../../components/states';
import { TextField } from '../../components/TextField';
import { toLocalDate } from '../../lib/dates';
import { CURRENCIES } from '../../lib/money';
import { useDebouncedValue } from '../../lib/useDebouncedValue';
import { DESKTOP, useMediaQuery } from '../../lib/useMediaQuery';
import { errorMessage } from '../errorMessage';
import { ExpenseForm } from './ExpenseForm';
import { ExpenseList } from './ExpenseList';
import { ExpenseTabs } from './ExpenseTabs';
import { memberName } from './splitState';
import { categoryLabel, type Expense } from './types';

const PAGE_SIZE = 50;

/** Filters live in the URL, so a filtered list can be shared and survives a reload. */
const FILTERS = ['month', 'status', 'categoryId', 'payerMemberId', 'currency', 'q'] as const;

type FilterName = (typeof FILTERS)[number];

/** Query parameter names (not UI text). */
const MONTH: FilterName = 'month';
const STATUS: FilterName = 'status';
const CATEGORY: FilterName = 'categoryId';
const PAYER: FilterName = 'payerMemberId';
const CURRENCY: FilterName = 'currency';
const TEXT: FilterName = 'q';

type Editing = { kind: 'new' } | { kind: 'edit'; expense: Expense } | null;

/**
 * Gastos: the band's expenses by month, with filters. Members record expenses ("Novo gasto"),
 * split them and mark planned ones as paid; each row offers what the user may do.
 */
export function ExpensesPage() {
  const { t } = useTranslation();
  const { can, me } = useBandAccess();
  const [params, setParams] = useSearchParams();
  const [editing, setEditing] = useState<Editing>(() =>
    params.get('new') === '1' ? { kind: 'new' } : null,
  );
  const [text, setText] = useState(params.get('q') ?? '');
  const desktop = useMediaQuery(DESKTOP);
  const [filtersOpen, setFiltersOpen] = useState(
    () => desktop || FILTERS.some((name) => params.get(name)),
  );
  const q = useDebouncedValue(text.trim(), 300);
  const page = Number(params.get('page') ?? '0') || 0;
  const filter = Object.fromEntries(
    FILTERS.filter((name) => name !== 'q')
      .map((name) => [name, params.get(name) ?? ''])
      .filter(([, value]) => value !== ''),
  ) as Record<string, string>;

  const band = useQuery({
    queryKey: ['band'],
    queryFn: async () => unwrap(await api.GET('/band')),
  });
  const members = useQuery({
    queryKey: ['members'],
    queryFn: async () => unwrap(await api.GET('/members')),
  });
  const categories = useQuery({
    queryKey: ['expense-categories'],
    queryFn: async () => unwrap(await api.GET('/expense-categories')),
  });
  const expenses = useQuery({
    queryKey: ['expenses', 'list', filter, q, page],
    queryFn: async () =>
      unwrap(
        await api.GET('/expenses', {
          params: {
            query: {
              ...filter,
              ...(q ? { q } : {}),
              page,
              size: PAGE_SIZE,
            } as Record<string, string | number>,
          },
        }),
      ),
    placeholderData: keepPreviousData,
  });

  const setFilter = (name: FilterName, value: string) => {
    const next = new URLSearchParams(params);
    if (value) {
      next.set(name, value);
    } else {
      next.delete(name);
    }
    next.delete('page');
    next.delete('new');
    setParams(next, { replace: true });
  };
  /** Value and change handler of a filter field. */
  const bind = (name: FilterName) => ({
    value: params.get(name) ?? '',
    onChange: (event: { target: { value: string } }) => setFilter(name, event.target.value),
  });
  const edit = (expense: Expense) => setEditing({ kind: 'edit', expense });
  const startNew = () => setEditing({ kind: 'new' });
  const setPage = (next: number) => {
    const updated = new URLSearchParams(params);
    if (next > 0) {
      updated.set('page', String(next));
    } else {
      updated.delete('page');
    }
    setParams(updated);
  };

  const ready = band.data && members.data && categories.data;
  const filtersInUrl = FILTERS.some((name) => params.get(name));
  const timezone = band.data?.timezone;
  const today = toLocalDate(new Date(), timezone);
  const ownMemberId = members.data?.find((member) => member.userId === me.data?.id)?.id;
  const filtered = filtersInUrl || q !== '';
  const canWrite = can('finance:write');

  return (
    <>
      <PageHeader
        title={t('expenses.title')}
        subtitle={t('expenses.subtitle')}
        action={
          canWrite && editing === null ? (
            <button type="button" className={primaryButton} onClick={startNew}>
              {t('expenses.new')}
            </button>
          ) : null
        }
      />
      <ExpenseTabs />
      {editing && ready ? (
        <ExpenseForm
          key={editing.kind === 'edit' ? editing.expense.id : 'new'}
          {...(editing.kind === 'edit' ? { expense: editing.expense } : {})}
          members={members.data}
          categories={categories.data}
          defaultCurrency={band.data.currency}
          today={today}
          ownMemberId={ownMemberId}
          onDone={() => setEditing(null)}
        />
      ) : null}
      <details
        className="rounded-card border-border bg-surface border p-4"
        open={filtersOpen}
        onToggle={(event) => setFiltersOpen(event.currentTarget.open)}
      >
        <summary className="text-card-title text-muted cursor-pointer font-semibold">
          {t('expenses.filters')}
        </summary>
        <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <TextField type="month" label={t('expenses.month')} {...bind(MONTH)} />
          <SelectField
            label={t('expenses.status')}
            {...bind(STATUS)}
            options={[
              { value: '', label: t('expenses.allStatuses') },
              { value: 'PAID', label: t('expenses.onlyPaid') },
              { value: 'PLANNED', label: t('expenses.onlyPlanned') },
            ]}
          />
          <SelectField
            label={t('expenses.category')}
            {...bind(CATEGORY)}
            options={[
              { value: '', label: t('expenses.allCategories') },
              ...(categories.data ?? []).map((category) => ({
                value: category.id,
                label: categoryLabel(t, category),
              })),
            ]}
          />
          <SelectField
            label={t('expenses.payer')}
            {...bind(PAYER)}
            options={[
              { value: '', label: t('expenses.allMembers') },
              ...(members.data ?? []).map((member) => ({
                value: member.id,
                label: memberName(t, member),
              })),
            ]}
          />
          <SelectField
            label={t('expenses.currency')}
            {...bind(CURRENCY)}
            options={[
              { value: '', label: t('expenses.allCurrencies') },
              ...CURRENCIES.map((code) => ({ value: code, label: t(`currencies.${code}`) })),
            ]}
          />
          <TextField
            type="search"
            label={t('expenses.search')}
            value={text}
            onChange={(event) => {
              setText(event.target.value);
              setFilter(TEXT, event.target.value.trim());
            }}
          />
        </div>
        {filtered ? (
          <button
            type="button"
            className={`${textLink} mt-3`}
            onClick={() => {
              setText('');
              setParams(new URLSearchParams(), { replace: true });
            }}
          >
            {t('expenses.clearFilters')}
          </button>
        ) : null}
      </details>
      {expenses.isPending || !ready ? <LoadingState /> : null}
      {expenses.isError ? (
        <ErrorState
          message={errorMessage(t, expenses.error)}
          onRetry={() => void expenses.refetch()}
        />
      ) : null}
      {expenses.data && ready && expenses.data.items.length === 0 ? (
        <EmptyState
          title={filtered ? t('expenses.emptyFiltered') : t('expenses.empty')}
          {...(filtered || !canWrite ? {} : { description: t('expenses.emptyHint') })}
        />
      ) : null}
      {expenses.data && ready && expenses.data.items.length > 0 ? (
        <ExpenseList
          expenses={expenses.data.items}
          members={members.data}
          categories={categories.data}
          ownMemberId={ownMemberId}
          today={today}
          onEdit={edit}
        />
      ) : null}
      {expenses.data && expenses.data.totalPages > 1 ? (
        <nav aria-label={t('expenses.pageOf', { page: page + 1, total: expenses.data.totalPages })}>
          <div className="flex items-center justify-between gap-3">
            <button
              type="button"
              className={secondaryButton}
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
            >
              {t('expenses.previousPage')}
            </button>
            <span className="text-muted">
              {t('expenses.pageOf', { page: page + 1, total: expenses.data.totalPages })}
            </span>
            <button
              type="button"
              className={secondaryButton}
              disabled={page + 1 >= expenses.data.totalPages}
              onClick={() => setPage(page + 1)}
            >
              {t('expenses.nextPage')}
            </button>
          </div>
        </nav>
      ) : null}
    </>
  );
}
