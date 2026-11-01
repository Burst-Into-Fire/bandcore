import { useTranslation } from 'react-i18next';
import { Navigate, Outlet, useLocation } from 'react-router';

import { useSession } from './useSession';

/** Route guard: waits for the session to be restored, then sends anonymous users to login. */
export function RequireSession() {
  const { t } = useTranslation();
  const session = useSession();
  const location = useLocation();

  if (session.status === 'unknown') {
    return (
      <main className="mx-auto max-w-md px-4 py-10">
        <p role="status" className="text-muted">
          {t('session.loading')}
        </p>
      </main>
    );
  }
  if (session.status === 'anonymous') {
    const redirect = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }
  return <Outlet />;
}
