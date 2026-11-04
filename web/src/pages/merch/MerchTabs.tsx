import { useTranslation } from 'react-i18next';
import { NavLink } from 'react-router';

/** Produtos, Estoque and Lotes, three views of the merch section. */
export function MerchTabs() {
  const { t } = useTranslation();
  const tab = ({ isActive }: { isActive: boolean }) =>
    `rounded-lg px-3 py-1.5 font-semibold ${
      isActive ? 'bg-surface-raised text-foreground' : 'text-muted hover:text-foreground'
    }`;
  return (
    <nav aria-label={t('merch.sections')} className="-mt-2 flex flex-wrap gap-2">
      <NavLink to="/merch" end className={tab}>
        {t('merch.tabProducts')}
      </NavLink>
      <NavLink to="/merch/stock" className={tab}>
        {t('merch.tabStock')}
      </NavLink>
      <NavLink to="/merch/batches" className={tab}>
        {t('merch.tabBatches')}
      </NavLink>
    </nav>
  );
}
