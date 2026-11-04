import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

import { api } from '../../api/client';
import { unwrap } from '../../api/problem';
import { ErrorState, LoadingState } from '../../components/states';
import { formatDateTime } from '../../lib/dates';
import { errorMessage } from '../errorMessage';

const PAGE_SIZE = 20;

/** "+3" or "−2": the sign is part of the number. */
function signed(delta: number): string {
  return delta > 0 ? `+${delta}` : `−${Math.abs(delta)}`;
}

/** The latest stock movements of a variant, newest first. */
export function MovementHistory({
  variantId,
  timeZone,
}: {
  variantId: string;
  timeZone: string | undefined;
}) {
  const { t } = useTranslation();
  const movements = useQuery({
    queryKey: ['movements', variantId],
    queryFn: async () =>
      unwrap(
        await api.GET('/variants/{variantId}/movements', {
          params: { path: { variantId }, query: { size: PAGE_SIZE } },
        }),
      ),
  });

  if (movements.isPending) {
    return <LoadingState />;
  }
  if (movements.isError) {
    return (
      <ErrorState
        message={errorMessage(t, movements.error)}
        onRetry={() => void movements.refetch()}
      />
    );
  }
  if (movements.data.items.length === 0) {
    return <p className="text-muted">{t('stock.historyEmpty')}</p>;
  }
  return (
    <ul aria-label={t('stock.history')} className="flex flex-col">
      {movements.data.items.map((movement) => (
        <li
          key={movement.id}
          className="border-border flex items-start justify-between gap-3 border-b py-2 last:border-b-0"
        >
          <span className="flex min-w-0 flex-col">
            <span>
              {movement.reason
                ? `${t(`stock.movement.${movement.type}`)} · ${t(
                    `stock.reasons.${movement.reason as 'COUNT' | 'DAMAGED' | 'LOST' | 'GIFT' | 'OTHER'}`,
                  )}`
                : t(`stock.movement.${movement.type}`)}
            </span>
            <span className="text-caption text-muted">
              {formatDateTime(movement.occurredAt, timeZone)}
            </span>
            {movement.note ? (
              <span className="text-caption text-muted truncate">{movement.note}</span>
            ) : null}
          </span>
          <span className="flex shrink-0 flex-col items-end">
            <span className="font-semibold tabular-nums">{signed(movement.quantityDelta)}</span>
            <span className="text-caption text-muted">
              {t('stock.after', { count: movement.quantityAfter })}
            </span>
          </span>
        </li>
      ))}
    </ul>
  );
}
