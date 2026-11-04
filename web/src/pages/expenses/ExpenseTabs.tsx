import { useTranslation } from 'react-i18next';
import { NavLink } from 'react-router';

/** Gastos and Acertos, two views of the same section. */
export function ExpenseTabs() {
  const { t } = useTranslation();
  const tab = ({ isActive }: { isActive: boolean }) =>
    `rounded-lg px-3 py-1.5 font-semibold ${
      isActive ? 'bg-surface-raised text-foreground' : 'text-muted hover:text-foreground'
    }`;
  return (
    <nav aria-label={t('settlements.sections')} className="-mt-2 flex gap-2">
      <NavLink to="/expenses" end className={tab}>
        {t('settlements.tabExpenses')}
      </NavLink>
      <NavLink to="/expenses/settlements" className={tab}>
        {t('settlements.tabSettlements')}
      </NavLink>
    </nav>
  );
}
