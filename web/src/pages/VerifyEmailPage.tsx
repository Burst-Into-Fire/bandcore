import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useNavigate } from 'react-router';
import type { z } from 'zod';

import { api } from '../api/client';
import { unwrap } from '../api/problem';
import { resendSchema } from '../auth/schemas';
import { AuthLayout } from '../components/AuthLayout';
import { primaryButton } from '../components/buttons';
import { FormAlert } from '../components/FormAlert';
import { TextField } from '../components/TextField';
import { translateKey } from '../i18n/messages';
import { errorMessage } from './errorMessage';
import { ResendVerification } from './ResendVerification';

type ResendInput = z.input<typeof resendSchema>;
type ResendValues = z.output<typeof resendSchema>;

/**
 * Landing page of the verification link (`/verify-email#token=...`). The token travels in the
 * fragment, which browsers never send to servers, and is removed from the address bar at once.
 */
export function VerifyEmailPage() {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const [token] = useState(() => new URLSearchParams(location.hash.slice(1)).get('token'));
  const started = useRef(false);
  const verify = useMutation({
    mutationFn: async (value: string) =>
      unwrap(await api.POST('/auth/verify-email', { body: { token: value } })),
  });
  const { mutate } = verify;

  useEffect(() => {
    if (token && !started.current) {
      started.current = true;
      mutate(token);
      void navigate(location.pathname, { replace: true });
    }
  }, [token, mutate, navigate, location.pathname]);

  return (
    <AuthLayout title={t('verify.title')}>
      {!token ? <FormAlert message={t('verify.missing')} /> : null}
      {token && (verify.isPending || verify.isIdle) ? (
        <p role="status" className="text-muted">
          {t('verify.verifying')}
        </p>
      ) : null}
      {verify.isSuccess ? (
        <>
          <p role="status">{t('verify.success')}</p>
          <Link to="/login" className={primaryButton + ' text-center'}>
            {t('verify.login')}
          </Link>
        </>
      ) : null}
      {verify.isError ? <FormAlert message={errorMessage(t, verify.error)} /> : null}
      {!token || verify.isError ? <RequestNewLink /> : null}
    </AuthLayout>
  );
}

function RequestNewLink() {
  const { t } = useTranslation();
  const [email, setEmail] = useState<string | null>(null);
  const form = useForm<ResendInput, unknown, ResendValues>({
    resolver: zodResolver(resendSchema),
    defaultValues: { email: '' },
  });

  if (email) {
    return <ResendVerification email={email} />;
  }
  return (
    <form
      noValidate
      className="flex flex-col gap-4"
      onSubmit={form.handleSubmit((values) => setEmail(values.email))}
    >
      <h2 className="text-card-title font-semibold">{t('verify.resendTitle')}</h2>
      <TextField
        label={t('fields.email')}
        type="email"
        autoComplete="email"
        error={translateKey(t, form.formState.errors.email?.message)}
        {...form.register('email')}
      />
      <button type="submit" className={primaryButton}>
        {t('verify.resend')}
      </button>
    </form>
  );
}
