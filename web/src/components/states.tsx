import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { secondaryButton } from './buttons';

/** Waiting for data; announced to screen readers. */
export function LoadingState({ label }: { label?: string }) {
  const { t } = useTranslation();
  return (
    <p role="status" className="text-muted py-6">
      {label ?? t('common.loading')}
    </p>
  );
}

/** Nothing to show yet, with an optional next step. */
export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="rounded-card border-border flex flex-col items-start gap-2 border border-dashed p-6">
      <p className="font-semibold">{title}</p>
      {description ? <p className="text-muted">{description}</p> : null}
      {action}
    </div>
  );
}

/** A request failed; offers to try again. */
export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  const { t } = useTranslation();
  return (
    <div
      role="alert"
      className="border-warning flex flex-col items-start gap-3 rounded-lg border p-4"
    >
      <p className="text-warning">{message}</p>
      {onRetry ? (
        <button type="button" className={secondaryButton} onClick={onRetry}>
          {t('common.retry')}
        </button>
      ) : null}
    </div>
  );
}
