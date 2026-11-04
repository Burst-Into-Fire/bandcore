import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { api } from '../api/client';
import { unwrap } from '../api/problem';
import { useBandAccess } from '../auth/useBandAccess';
import { Card, KpiTile, ValueList } from '../components/Card';
import { primaryButton, textLink } from '../components/buttons';
import { PageHeader } from '../components/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '../components/states';
import { formatMoney } from '../lib/money';
import { errorMessage } from './errorMessage';
import { ExpenseBadge } from './expenses/ExpenseList';
import { roleLabel } from './roles';
import { SettlementCard } from './settlements/SettlementCard';

/**
 * Resumo: the band's dashboard, with the latest expenses. The figures arrive with balances (M6)
 * and merch (M7); until then the cards say what each one will show.
 */
export function SummaryPage() {
  const { t } = useTranslation();
  const { me, role, can } = useBandAccess();

  if (me.isPending) {
    return <LoadingState />;
  }
  if (me.isError) {
    return <ErrorState message={errorMessage(t, me.error)} onRetry={() => void me.refetch()} />;
  }
  return (
    <>
      <PageHeader
        title={t('summary.title')}
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
      <div className="grid gap-3 sm:grid-cols-3">
        <KpiTile label={t('summary.monthExpenses')} value="—" caption={t('summary.soon')} />
        <KpiTile label={t('summary.merchSales')} value="—" caption={t('summary.soon')} />
        <KpiTile label={t('summary.merchProfit')} value="—" caption={t('summary.soon')} />
      </div>
      <div className="grid gap-3 lg:grid-cols-2">
        <SettlementCard showHistoryLink />
        <Card title={t('summary.stock')}>
          <EmptyState title={t('summary.stockEmpty')} description={t('summary.stockHint')} />
        </Card>
      </div>
      <LatestExpenses />
    </>
  );
}

/** The five most recent expenses, linking to Gastos. */
function LatestExpenses() {
  const { t } = useTranslation();
  const latest = useQuery({
    queryKey: ['expenses', 'latest'],
    queryFn: async () => unwrap(await api.GET('/expenses', { params: { query: { size: 5 } } })),
  });
  return (
    <Card
      title={t('summary.latestExpenses')}
      action={
        <Link to="/expenses" className={textLink}>
          {t('summary.allExpenses')}
        </Link>
      }
    >
      {latest.isPending ? <LoadingState /> : null}
      {latest.isError ? (
        <ErrorState message={errorMessage(t, latest.error)} onRetry={() => void latest.refetch()} />
      ) : null}
      {latest.data?.items.length === 0 ? (
        <EmptyState
          title={t('summary.latestExpensesEmpty')}
          description={t('summary.latestExpensesHint')}
        />
      ) : null}
      {latest.data && latest.data.items.length > 0 ? (
        <ValueList
          items={latest.data.items.map((expense) => ({
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
