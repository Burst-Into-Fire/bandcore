import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

import { api } from '../api/client';

export function HomePage() {
  const { t } = useTranslation();
  const status = useQuery({
    queryKey: ['status'],
    queryFn: async () => {
      const { data, response } = await api.GET('/status');
      if (!response.ok || !data) {
        throw new Error(`Status check failed with ${response.status}`);
      }
      return data;
    },
  });

  const statusText = status.isPending
    ? t('home.apiStatus.loading')
    : status.data?.status === 'UP'
      ? t('home.apiStatus.online')
      : t('home.apiStatus.offline');

  return (
    <main className="mx-auto flex min-h-screen max-w-3xl flex-col gap-6 px-4 py-10">
      <header className="flex flex-col gap-1">
        <p className="text-card-title text-accent font-semibold tracking-wide uppercase">
          {t('app.name')}
        </p>
        <h1 className="text-title font-bold">{t('home.title')}</h1>
        <p className="text-muted">{t('app.tagline')}</p>
      </header>

      <section
        aria-labelledby="api-status-label"
        className="rounded-card border-border bg-surface border p-4"
      >
        <h2 id="api-status-label" className="text-card-title text-muted">
          {t('home.apiStatus.label')}
        </h2>
        <p
          role="status"
          className={`text-kpi font-semibold ${status.isError ? 'text-warning' : 'text-foreground'}`}
        >
          {statusText}
        </p>
      </section>
    </main>
  );
}
