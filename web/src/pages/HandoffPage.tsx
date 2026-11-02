import { useMutation } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useNavigate } from 'react-router';

import { api } from '../api/client';
import { unwrap } from '../api/problem';
import { startSession } from '../auth/session';
import { AuthLayout } from '../components/AuthLayout';
import { primaryButton } from '../components/buttons';
import { FormAlert } from '../components/FormAlert';
import { useHost } from '../lib/host';

/**
 * Landing page of a handoff link (`/auth/handoff#code=...`) on a band or the central host. The
 * code travels in the fragment, is removed from the address bar at once and exchanged for a
 * session on this host.
 */
export function HandoffPage() {
  const { t } = useTranslation();
  const host = useHost();
  const location = useLocation();
  const navigate = useNavigate();
  const [code] = useState(() => new URLSearchParams(location.hash.slice(1)).get('code'));
  const started = useRef(false);
  const home = host.kind === 'band' ? '/' : '/bands';
  const exchange = useMutation({
    mutationFn: async (value: string) =>
      unwrap(await api.POST('/auth/handoff/exchange', { body: { code: value } })),
    onSuccess: (session) => {
      startSession(session.accessToken);
      void navigate(home, { replace: true });
    },
  });
  const { mutate } = exchange;

  useEffect(() => {
    if (code && !started.current) {
      started.current = true;
      void navigate(location.pathname, { replace: true });
      mutate(code);
    }
  }, [code, mutate, navigate, location.pathname]);

  if (!code || exchange.isError) {
    return (
      <AuthLayout title={t('handoff.failedTitle')}>
        <FormAlert message={t('handoff.failed')} />
        <Link to="/login" className={primaryButton + ' text-center'}>
          {t('handoff.login')}
        </Link>
      </AuthLayout>
    );
  }
  return (
    <AuthLayout title={t('handoff.title')}>
      <p role="status" className="text-muted">
        {t('handoff.opening')}
      </p>
    </AuthLayout>
  );
}
