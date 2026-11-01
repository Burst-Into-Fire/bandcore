import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import type { z } from 'zod';

import { api } from '../api/client';
import { applyProblem } from '../api/forms';
import { unwrap } from '../api/problem';
import { signupSchema } from '../auth/schemas';
import { AuthLayout } from '../components/AuthLayout';
import { primaryButton, textLink } from '../components/buttons';
import { FormAlert } from '../components/FormAlert';
import { TextField } from '../components/TextField';
import { translateKey } from '../i18n/messages';
import { ResendVerification } from './ResendVerification';

type SignupInput = z.input<typeof signupSchema>;
type SignupValues = z.output<typeof signupSchema>;

export function SignupPage() {
  const { t } = useTranslation();
  const [sentTo, setSentTo] = useState<string | null>(null);
  const form = useForm<SignupInput, unknown, SignupValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: { displayName: '', email: '', password: '' },
  });
  const signup = useMutation({
    mutationFn: async (values: SignupValues) =>
      unwrap(await api.POST('/auth/signup', { body: values, parseAs: 'text' })),
    onSuccess: (_data, values) => setSentTo(values.email),
    onError: (error) => applyProblem(error, form.setError, ['displayName', 'email', 'password']),
  });
  const { errors } = form.formState;

  if (sentTo) {
    return (
      <AuthLayout title={t('signup.sentTitle')}>
        <p>{t('signup.sentBody', { email: sentTo })}</p>
        <ResendVerification email={sentTo} />
        <Link to="/login" className={textLink}>
          {t('signup.login')}
        </Link>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title={t('signup.title')}>
      <form
        noValidate
        className="flex flex-col gap-4"
        onSubmit={form.handleSubmit((values) => signup.mutate(values))}
      >
        <FormAlert message={translateKey(t, errors.root?.server?.message)} />
        <TextField
          label={t('fields.displayName')}
          autoComplete="name"
          error={translateKey(t, errors.displayName?.message)}
          {...form.register('displayName')}
        />
        <TextField
          label={t('fields.email')}
          type="email"
          autoComplete="email"
          inputMode="email"
          error={translateKey(t, errors.email?.message)}
          {...form.register('email')}
        />
        <TextField
          label={t('fields.password')}
          type="password"
          autoComplete="new-password"
          hint={t('signup.passwordHint')}
          error={translateKey(t, errors.password?.message)}
          {...form.register('password')}
        />
        <button type="submit" className={primaryButton} disabled={signup.isPending}>
          {signup.isPending ? t('signup.submitting') : t('signup.submit')}
        </button>
      </form>
      <p className="text-muted">
        {t('signup.haveAccount')}{' '}
        <Link to="/login" className={textLink}>
          {t('signup.login')}
        </Link>
      </p>
    </AuthLayout>
  );
}
