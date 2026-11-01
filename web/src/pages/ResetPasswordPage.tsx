import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useNavigate } from 'react-router';
import type { z } from 'zod';

import { api } from '../api/client';
import { applyProblem } from '../api/forms';
import { ApiError, unwrap } from '../api/problem';
import { resetPasswordSchema } from '../auth/schemas';
import { AuthLayout } from '../components/AuthLayout';
import { primaryButton, textLink } from '../components/buttons';
import { FormAlert } from '../components/FormAlert';
import { TextField } from '../components/TextField';
import { translateKey } from '../i18n/messages';

type ResetInput = z.input<typeof resetPasswordSchema>;
type ResetValues = z.output<typeof resetPasswordSchema>;

/**
 * Landing page of the reset link (`/reset-password#token=...`). The token travels in the
 * fragment, which browsers never send to servers, and is removed from the address bar at once.
 */
export function ResetPasswordPage() {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const [token] = useState(() => new URLSearchParams(location.hash.slice(1)).get('token'));
  const form = useForm<ResetInput, unknown, ResetValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { password: '' },
  });
  const reset = useMutation({
    mutationFn: async (values: ResetValues) =>
      unwrap(
        await api.POST('/auth/password-reset/confirm', {
          body: { token: token ?? '', password: values.password },
          parseAs: 'text',
        }),
      ),
    onError: (error) => applyProblem(error, form.setError, ['password']),
  });
  const { errors } = form.formState;

  useEffect(() => {
    if (location.hash) {
      void navigate(location.pathname, { replace: true });
    }
  }, [location.hash, location.pathname, navigate]);

  if (!token) {
    return (
      <AuthLayout title={t('resetPassword.title')}>
        <FormAlert message={t('resetPassword.missing')} />
        <RequestNewLink />
      </AuthLayout>
    );
  }

  if (reset.isSuccess) {
    return (
      <AuthLayout title={t('resetPassword.title')}>
        <p role="status">{t('resetPassword.success')}</p>
        <Link to="/login" className={primaryButton + ' text-center'}>
          {t('resetPassword.login')}
        </Link>
      </AuthLayout>
    );
  }

  const tokenRejected = reset.error instanceof ApiError && reset.error.code === 'TOKEN_INVALID';
  return (
    <AuthLayout title={t('resetPassword.title')} subtitle={t('resetPassword.intro')}>
      <form
        noValidate
        className="flex flex-col gap-4"
        onSubmit={form.handleSubmit((values) => reset.mutate(values))}
      >
        <FormAlert message={translateKey(t, errors.root?.server?.message)} />
        <TextField
          label={t('fields.newPassword')}
          type="password"
          autoComplete="new-password"
          hint={t('signup.passwordHint')}
          error={translateKey(t, errors.password?.message)}
          {...form.register('password')}
        />
        <button type="submit" className={primaryButton} disabled={reset.isPending}>
          {reset.isPending ? t('resetPassword.submitting') : t('resetPassword.submit')}
        </button>
      </form>
      {tokenRejected ? <RequestNewLink /> : null}
    </AuthLayout>
  );
}

function RequestNewLink() {
  const { t } = useTranslation();
  return (
    <Link to="/forgot-password" className={textLink}>
      {t('resetPassword.requestNew')}
    </Link>
  );
}
