import { useMutation, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

import { api } from '../api/client';
import { unwrap } from '../api/problem';
import { useLogout } from '../auth/useLogout';
import { secondaryButton } from '../components/buttons';
import { FormAlert } from '../components/FormAlert';
import { errorMessage } from './errorMessage';
import { roleLabel } from './roles';

/** Landing page of a band host after login. The application shell arrives in M4-06. */
export function BandHomePage() {
  const { t } = useTranslation();
  const logout = useLogout();
  const me = useQuery({ queryKey: ['me'], queryFn: async () => unwrap(await api.GET('/me')) });
  const band = useQuery({
    queryKey: ['band'],
    queryFn: async () => unwrap(await api.GET('/band')),
  });
  const logoutEverywhere = useMutation({
    mutationFn: async () => unwrap(await api.POST('/auth/logout-all')),
    onSettled: () => void logout(),
  });

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-3xl flex-col gap-6 px-4 py-10">
      <header className="flex flex-col gap-1">
        <p className="text-card-title text-accent font-semibold tracking-wide uppercase">
          {t('app.name')}
        </p>
        <h1 className="text-title font-bold">{band.data?.name ?? '…'}</h1>
      </header>
      {me.isError ? <FormAlert message={errorMessage(t, me.error)} /> : null}
      {me.data ? (
        <section className="rounded-card border-border bg-surface flex flex-col gap-1 border p-4">
          <p className="font-semibold">{t('bandHome.welcome', { name: me.data.displayName })}</p>
          {me.data.band ? (
            <p className="text-muted">
              {t('bandHome.role', { role: roleLabel(t, me.data.band.role) })}
            </p>
          ) : null}
        </section>
      ) : null}
      <p className="text-muted">{t('bandHome.comingSoon')}</p>
      <div className="flex flex-col gap-2 sm:flex-row">
        <button type="button" className={secondaryButton} onClick={() => void logout()}>
          {t('common.logout')}
        </button>
        <button
          type="button"
          className={secondaryButton}
          disabled={logoutEverywhere.isPending}
          onClick={() => logoutEverywhere.mutate()}
        >
          {t('common.logoutEverywhere')}
        </button>
      </div>
    </main>
  );
}
