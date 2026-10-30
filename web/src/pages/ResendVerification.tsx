import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

import { api } from '../api/client';
import { unwrap } from '../api/problem';
import { secondaryButton } from '../components/buttons';
import { FormAlert } from '../components/FormAlert';
import { errorMessage } from './errorMessage';

/** Sends a new verification link; the API answers the same whether or not the email has an account. */
export function ResendVerification({ email }: { email: string }) {
  const { t } = useTranslation();
  const resend = useMutation({
    mutationFn: async () =>
      unwrap(await api.POST('/auth/resend-verification', { body: { email }, parseAs: 'text' })),
  });

  return (
    <div className="flex flex-col gap-2">
      <button
        type="button"
        className={secondaryButton}
        disabled={resend.isPending}
        onClick={() => resend.mutate()}
      >
        {t('signup.resend')}
      </button>
      {resend.isSuccess ? (
        <p role="status" className="text-muted">
          {t('signup.resent')}
        </p>
      ) : null}
      <FormAlert message={resend.isError ? errorMessage(t, resend.error) : undefined} />
    </div>
  );
}
