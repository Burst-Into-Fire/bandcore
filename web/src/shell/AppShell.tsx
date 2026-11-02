import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { NavLink, Outlet } from 'react-router';

import { api } from '../api/client';
import { unwrap } from '../api/problem';
import { useBandAccess } from '../auth/useBandAccess';
import { DESKTOP, useMediaQuery } from '../lib/useMediaQuery';
import { SECTIONS } from './navigation';
import { UserMenu } from './UserMenu';

/**
 * The band workspace: sidebar on desktop; compact header and bottom navigation up to 768 px.
 * Only one of the two is rendered, so screen readers never meet the navigation twice.
 */
export function AppShell() {
  const { t } = useTranslation();
  const desktop = useMediaQuery(DESKTOP);
  const { can } = useBandAccess();
  const band = useQuery({
    queryKey: ['band'],
    queryFn: async () => unwrap(await api.GET('/band')),
  });
  const sections = SECTIONS.filter((section) => !section.permission || can(section.permission));
  const bandName = band.data?.name ?? '…';

  return (
    <div className="min-h-screen md:flex">
      <a
        href="#main"
        className="bg-accent text-accent-foreground sr-only z-50 rounded-lg px-3 py-2 focus:not-sr-only focus:fixed focus:top-2 focus:left-2"
      >
        {t('shell.skipToContent')}
      </a>
      {desktop ? (
        <aside className="border-border bg-surface sticky top-0 flex h-screen w-60 shrink-0 flex-col gap-6 border-r p-4">
          <div className="flex flex-col gap-1 px-2">
            <p className="text-caption text-accent font-semibold tracking-wide uppercase">
              {t('app.name')}
            </p>
            <p className="truncate text-lg font-bold">{bandName}</p>
          </div>
          <nav aria-label={t('shell.navigation')} className="flex flex-1 flex-col gap-1">
            {sections.map((section) => (
              <NavLink
                key={section.path}
                to={section.path}
                end={section.path === '/'}
                className={({ isActive }) =>
                  `flex items-center gap-3 rounded-lg px-3 py-2 font-medium ${
                    isActive
                      ? 'bg-surface-raised text-foreground'
                      : 'text-muted hover:bg-surface-raised hover:text-foreground'
                  }`
                }
              >
                <section.icon />
                {t(section.labelKey)}
              </NavLink>
            ))}
          </nav>
          <UserMenu placement="up" />
        </aside>
      ) : (
        <header className="border-border bg-surface sticky top-0 z-20 flex items-center justify-between gap-3 border-b px-4 py-3">
          <p className="min-w-0 truncate font-bold">{bandName}</p>
          <UserMenu placement="down" />
        </header>
      )}
      <main id="main" tabIndex={-1} className="min-w-0 flex-1 px-4 py-6 pb-24 md:px-8 md:pb-8">
        <div className="mx-auto flex w-full max-w-5xl flex-col gap-6">
          <Outlet />
        </div>
      </main>
      {desktop ? null : (
        <nav
          aria-label={t('shell.navigation')}
          className="border-border bg-surface fixed inset-x-0 bottom-0 z-20 flex border-t pb-[env(safe-area-inset-bottom)]"
        >
          {sections.map((section) => (
            <NavLink
              key={section.path}
              to={section.path}
              end={section.path === '/'}
              className={({ isActive }) =>
                `text-caption flex min-w-0 flex-1 flex-col items-center gap-1 py-2 font-medium ${
                  isActive ? 'text-accent' : 'text-muted hover:text-foreground'
                }`
              }
            >
              <section.icon />
              <span className="truncate">{t(section.shortLabelKey ?? section.labelKey)}</span>
            </NavLink>
          ))}
        </nav>
      )}
    </div>
  );
}
