import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { api } from '../api/client';
import { unwrap } from '../api/problem';
import { useLogout } from '../auth/useLogout';
import { AuthLayout } from '../components/AuthLayout';
import { primaryButton, secondaryButton, textLink } from '../components/buttons';
import { FormAlert } from '../components/FormAlert';
import { errorMessage } from './errorMessage';
import { roleLabel } from './roles';

/**
 * The user's bands on the central host. Each band is another origin with its own session, so
 * opening one goes to its login page (a seamless handoff arrives in M4-05).
 */
export function MyBandsPage() {
  const { t } = useTranslation();
  const logout = useLogout();
  const bands = useQuery({
    queryKey: ['my-bands'],
    queryFn: async () => unwrap(await api.GET('/me/bands')),
  });

  return (
    <AuthLayout title={t('bands.title')}>
      {bands.isError ? <FormAlert message={errorMessage(t, bands.error)} /> : null}
      {bands.data?.length === 0 ? <p className="text-muted">{t('bands.empty')}</p> : null}
      {bands.data && bands.data.length > 0 ? (
        <ul className="flex flex-col gap-2">
          {bands.data.map((band) => (
            <li
              key={band.id}
              className="rounded-card border-border bg-surface flex items-center justify-between gap-3 border p-4"
            >
              <div className="flex min-w-0 flex-col">
                <span className="truncate font-semibold">{band.name}</span>
                <span className="text-caption text-muted">{roleLabel(t, band.role)}</span>
              </div>
              <a href={`${band.url}/login`} className={textLink}>
                {t('bands.open')}
              </a>
            </li>
          ))}
        </ul>
      ) : null}
      <Link to="/bands/new" className={`${primaryButton} text-center`}>
        {t('bands.create')}
      </Link>
      <button type="button" className={secondaryButton} onClick={() => void logout()}>
        {t('common.logout')}
      </button>
    </AuthLayout>
  );
}
