import { useTranslation } from 'react-i18next';

import { useBandAccess } from '../auth/useBandAccess';
import { Card, KpiTile } from '../components/Card';
import { PageHeader } from '../components/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '../components/states';
import { errorMessage } from './errorMessage';
import { roleLabel } from './roles';

/**
 * Resumo: the band's dashboard. The figures arrive with expenses (M5), balances (M6) and merch
 * (M7); until then the cards say what each one will show.
 */
export function SummaryPage() {
  const { t } = useTranslation();
  const { me, role } = useBandAccess();

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
        <Card title={t('summary.settlement')}>
          <EmptyState
            title={t('summary.settlementEmpty')}
            description={t('summary.settlementHint')}
          />
        </Card>
        <Card title={t('summary.stock')}>
          <EmptyState title={t('summary.stockEmpty')} description={t('summary.stockHint')} />
        </Card>
      </div>
      <Card title={t('summary.latestExpenses')}>
        <EmptyState
          title={t('summary.latestExpensesEmpty')}
          description={t('summary.latestExpensesHint')}
        />
      </Card>
    </>
  );
}
