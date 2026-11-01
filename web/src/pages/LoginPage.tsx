import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useSearchParams } from 'react-router';
import type { z } from 'zod';

import { api } from '../api/client';
import { applyProblem } from '../api/forms';
import { unwrap } from '../api/problem';
import { loginSchema } from '../auth/schemas';
import { startSession } from '../auth/session';
import { AuthLayout } from '../components/AuthLayout';
import { primaryButton, textLink } from '../components/buttons';
import { FormAlert } from '../components/FormAlert';
import { TextField } from '../components/TextField';
import { translateKey } from '../i18n/messages';
import { useHost } from '../lib/host';
import { safeRedirect } from '../lib/navigation';

type LoginInput = z.input<typeof loginSchema>;
type LoginValues = z.output<typeof loginSchema>;

/** Login on the central host or on a band host; the session only exists on this host. */
export function LoginPage() {
  const { t } = useTranslation();
  const host = useHost();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const profile = useQuery({
    queryKey: ['band-public-profile'],
    queryFn: async () => unwrap(await api.GET('/band/public-profile')),
    enabled: host.kind === 'band',
    retry: false,
  });
  const form = useForm<LoginInput, unknown, LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  });
  const login = useMutation({
    mutationFn: async (values: LoginValues) =>
      unwrap(await api.POST('/auth/login', { body: values })),
    onSuccess: (session) => {
      startSession(session.accessToken);
      const fallback = host.kind === 'band' ? '/' : '/bands';
      void navigate(safeRedirect(searchParams.get('redirect'), fallback), { replace: true });
    },
    onError: (error) => {
      form.resetField('password');
      applyProblem(error, form.setError, ['email', 'password']);
    },
  });
  const { errors } = form.formState;

  if (host.kind === 'band' && profile.isError) {
    return (
      <AuthLayout title={t('login.bandNotFound')}>
        <span />
      </AuthLayout>
    );
  }

  const title =
    host.kind === 'band'
      ? t('login.titleBand', { band: profile.data?.name ?? host.slug })
      : t('login.titleCentral');

  return (
    <AuthLayout title={title} subtitle={host.kind === 'band' ? t('login.subtitleBand') : undefined}>
      <form
        noValidate
        className="flex flex-col gap-4"
        onSubmit={form.handleSubmit((values) => login.mutate(values))}
      >
        <FormAlert message={translateKey(t, errors.root?.server?.message)} />
        <TextField
          label={t('fields.email')}
          type="email"
          autoComplete="username"
          inputMode="email"
          error={translateKey(t, errors.email?.message)}
          {...form.register('email')}
        />
        <TextField
          label={t('fields.password')}
          type="password"
          autoComplete="current-password"
          error={translateKey(t, errors.password?.message)}
          {...form.register('password')}
        />
        <button type="submit" className={primaryButton} disabled={login.isPending}>
          {login.isPending ? t('login.submitting') : t('login.submit')}
        </button>
      </form>
      {host.kind === 'central' ? (
        <p className="text-muted">
          {t('login.noAccount')}{' '}
          <Link to="/signup" className={textLink}>
            {t('login.signUp')}
          </Link>
        </p>
      ) : null}
    </AuthLayout>
  );
}
