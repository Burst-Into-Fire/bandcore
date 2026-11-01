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
import { forgotPasswordSchema } from '../auth/schemas';
import { AuthLayout } from '../components/AuthLayout';
import { primaryButton, textLink } from '../components/buttons';
import { FormAlert } from '../components/FormAlert';
import { TextField } from '../components/TextField';
import { translateKey } from '../i18n/messages';

type ForgotInput = z.input<typeof forgotPasswordSchema>;
type ForgotValues = z.output<typeof forgotPasswordSchema>;

/** Asks for a reset link; the API answers the same whether or not the email has an account. */
export function ForgotPasswordPage() {
  const { t } = useTranslation();
  const [sentTo, setSentTo] = useState<string | null>(null);
  const form = useForm<ForgotInput, unknown, ForgotValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: '' },
  });
  const request = useMutation({
    mutationFn: async (values: ForgotValues) =>
      unwrap(await api.POST('/auth/password-reset', { body: values, parseAs: 'text' })),
    onSuccess: (_data, values) => setSentTo(values.email),
    onError: (error) => applyProblem(error, form.setError, ['email']),
  });
  const { errors } = form.formState;

  if (sentTo) {
    return (
      <AuthLayout title={t('forgotPassword.sentTitle')}>
        <p role="status">{t('forgotPassword.sentBody', { email: sentTo })}</p>
        <Link to="/login" className={textLink}>
          {t('forgotPassword.backToLogin')}
        </Link>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title={t('forgotPassword.title')} subtitle={t('forgotPassword.intro')}>
      <form
        noValidate
        className="flex flex-col gap-4"
        onSubmit={form.handleSubmit((values) => request.mutate(values))}
      >
        <FormAlert message={translateKey(t, errors.root?.server?.message)} />
        <TextField
          label={t('fields.email')}
          type="email"
          autoComplete="email"
          inputMode="email"
          error={translateKey(t, errors.email?.message)}
          {...form.register('email')}
        />
        <button type="submit" className={primaryButton} disabled={request.isPending}>
          {request.isPending ? t('forgotPassword.submitting') : t('forgotPassword.submit')}
        </button>
      </form>
      <Link to="/login" className={textLink}>
        {t('forgotPassword.backToLogin')}
      </Link>
    </AuthLayout>
  );
}
