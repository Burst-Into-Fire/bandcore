import { useTranslation } from 'react-i18next';

import { PageHeader } from '../components/PageHeader';
import { EmptyState } from '../components/states';

/** A section whose module is not built yet (Merch in M7). */
export function ComingSoonPage({ section }: { section: 'merch' }) {
  const { t } = useTranslation();
  return (
    <>
      <PageHeader title={t(`${section}.title`)} subtitle={t(`${section}.subtitle`)} />
      <EmptyState title={t('common.comingSoon')} description={t(`${section}.comingSoon`)} />
    </>
  );
}
