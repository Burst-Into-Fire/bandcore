import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router';

import { api } from '../api/client';
import { unwrap } from '../api/problem';
import { useBandAccess } from '../auth/useBandAccess';
import { primaryButton, secondaryButton, textLink } from '../components/buttons';
import { Card, KpiTile, ValueList } from '../components/Card';
import { PageHeader } from '../components/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '../components/states';
import { formatMoney, type Currency } from '../lib/money';
import { shiftMonth } from '../lib/months';
import { errorMessage } from './errorMessage';
import { ExpenseBadge } from './expenses/ExpenseList';
import { monthLabel } from './expenses/types';
import { roleLabel } from './roles';
import { SettlementCard } from './settlements/SettlementCard';

type FinanceSummary = Awaited<ReturnType<typeof fetchSummary>>;

async function fetchSummary(month: string | null) {
  return unwrap(await api.GET('/summary/finance', { params: { query: month ? { month } : {} } }));
}

/**
 * Resumo: the band's month at a glance. Gastos do mês per currency (paid and planned), who
 * receives and who owes, and the month's latest expenses; the merch cards arrive with M8. The
 * month is in the URL; without one, the API answers with the current month in the band's time
 * zone.
 */
export function SummaryPage() {
  const { t } = useTranslation();
  const { me, role, can } = useBandAccess();
  const [params, setParams] = useSearchParams();
  const requested = params.get('month');
  const band = useQuery({
    queryKey: ['band'],
    queryFn: async () => unwrap(await api.GET('/band')),
  });
  const summary = useQuery({
    queryKey: ['summary', 'finance', requested],
    queryFn: () => fetchSummary(requested),
    placeholderData: keepPreviousData,
  });

  if (me.isPending) {
    return <LoadingState />;
  }
  if (me.isError) {
    return <ErrorState message={errorMessage(t, me.error)} onRetry={() => void me.refetch()} />;
  }
  const month = summary.data?.month ?? requested;
  const goTo = (delta: number) => {
    if (month) {
      setParams({ month: shiftMonth(month, delta) });
    }
  };

  return (
    <>
      <PageHeader
        title={
          month
            ? t('summary.titleMonth', { month: monthLabel(`${month}-01`).toLowerCase() })
            : t('summary.title')
        }
        subtitle={t('bandHome.welcome', { name: me.data.displayName })}
        action={
          can('finance:write') ? (
            <Link to="/expenses?new=1" className={`${primaryButton} inline-block`}>
              {t('expenses.new')}
            </Link>
          ) : null
        }
      />
      {role ? (
        <p className="text-muted -mt-4">{t('bandHome.role', { role: roleLabel(t, role) })}</p>
      ) : null}
      <div className="-mt-2 flex gap-2">
        <button
          type="button"
          className={secondaryButton}
          disabled={!month}
          onClick={() => goTo(-1)}
        >
          {t('summary.previousMonth')}
        </button>
        <button type="button" className={secondaryButton} disabled={!month} onClick={() => goTo(1)}>
          {t('summary.nextMonth')}
        </button>
      </div>
      {summary.isError ? (
        <ErrorState
          message={errorMessage(t, summary.error)}
          onRetry={() => void summary.refetch()}
        />
      ) : null}
      <div className="grid gap-3 sm:grid-cols-3">
        <MonthExpenses summary={summary.data} defaultCurrency={band.data?.currency ?? 'BRL'} />
        <KpiTile label={t('summary.merchSales')} value="—" caption={t('summary.soon')} />
        <KpiTile label={t('summary.merchProfit')} value="—" caption={t('summary.soon')} />
      </div>
      <div className="grid gap-3 lg:grid-cols-2">
        <SettlementCard showHistoryLink />
        <Card title={t('summary.stock')}>
          <EmptyState title={t('summary.stockEmpty')} description={t('summary.stockHint')} />
        </Card>
      </div>
      <LatestExpenses summary={summary.data} pending={summary.isPending} />
    </>
  );
}

/**
 * Gastos do mês: the total in the band's currency with "Pago X | Previsto Y"; other currencies
 * get their own line, never added to it.
 */
function MonthExpenses({
  summary,
  defaultCurrency,
}: {
  summary: FinanceSummary | undefined;
  defaultCurrency: Currency;
}) {
  const { t } = useTranslation();
  const currencies = summary?.currencies ?? [];
  const main = currencies.find((item) => item.currency === defaultCurrency) ?? currencies[0];
  const others = currencies.filter((item) => item !== main);
  const caption = (item: (typeof currencies)[number]) =>
    t('summary.monthPaidPlanned', {
      paid: formatMoney(item.paidCents, item.currency),
      planned: formatMoney(item.plannedCents, item.currency),
    });
  return (
    <div className="rounded-card border-border bg-surface flex flex-col gap-1 border p-4">
      <p className="text-card-title text-muted">{t('summary.monthExpenses')}</p>
      <p className="text-kpi font-bold tabular-nums">
        {summary ? formatMoney(main?.totalCents ?? 0, main?.currency ?? defaultCurrency) : '—'}
      </p>
      <p className="text-caption text-muted">
        {main ? caption(main) : summary ? t('summary.noExpensesMonth') : t('common.loading')}
      </p>
      {others.map((item) => (
        <p key={item.currency} className="text-caption text-muted tabular-nums">
          {formatMoney(item.totalCents, item.currency)} · {caption(item)}
        </p>
      ))}
    </div>
  );
}

/** The month's latest expenses, linking to Gastos. */
function LatestExpenses({
  summary,
  pending,
}: {
  summary: FinanceSummary | undefined;
  pending: boolean;
}) {
  const { t } = useTranslation();
  return (
    <Card
      title={t('summary.latestExpenses')}
      action={
        <Link to="/expenses" className={textLink}>
          {t('summary.allExpenses')}
        </Link>
      }
    >
      {pending ? <LoadingState /> : null}
      {summary?.latest.length === 0 ? (
        <EmptyState
          title={t('summary.latestExpensesEmpty')}
          description={t('summary.latestExpensesHint')}
        />
      ) : null}
      {summary && summary.latest.length > 0 ? (
        <ValueList
          items={summary.latest.map((expense) => ({
            key: expense.id,
            label: expense.description,
            detail: <ExpenseBadge expense={expense} />,
            value: formatMoney(expense.amountCents, expense.currency),
          }))}
        />
      ) : null}
    </Card>
  );
}
