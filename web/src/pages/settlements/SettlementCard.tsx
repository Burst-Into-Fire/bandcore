import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';

import { api } from '../../api/client';
import { unwrap } from '../../api/problem';
import { useBandAccess } from '../../auth/useBandAccess';
import { secondaryButton, textLink } from '../../components/buttons';
import { Card } from '../../components/Card';
import { FormAlert } from '../../components/FormAlert';
import { EmptyState, ErrorState, LoadingState } from '../../components/states';
import { formatMoney } from '../../lib/money';
import { errorMessage } from '../errorMessage';
import { memberName } from '../expenses/splitState';
import type { Member } from '../expenses/types';
import { MONEY_QUERIES } from './types';

/**
 * Acerto entre integrantes: each member receives or owes, per currency, and the suggested
 * transfers that settle everything, each recorded with one click.
 */
export function SettlementCard({ showHistoryLink = false }: { showHistoryLink?: boolean }) {
  const { t } = useTranslation();
  const { can } = useBandAccess();
  const queryClient = useQueryClient();
  const members = useQuery({
    queryKey: ['members'],
    queryFn: async () => unwrap(await api.GET('/members')),
  });
  const balances = useQuery({
    queryKey: ['balances'],
    queryFn: async () => unwrap(await api.GET('/balances')),
  });
  const plan = useQuery({
    queryKey: ['settlement-plan'],
    queryFn: async () => unwrap(await api.GET('/balances/settlement-plan')),
  });
  const record = useMutation({
    mutationFn: async (transfer: {
      fromMemberId: string;
      toMemberId: string;
      currency: 'BRL' | 'USD';
      amountCents: number;
    }) => unwrap(await api.POST('/settlements', { body: transfer })),
    onSuccess: () =>
      Promise.all(
        MONEY_QUERIES.map((queryKey) => queryClient.invalidateQueries({ queryKey: [...queryKey] })),
      ),
  });

  const names = new Map<string, Member>((members.data ?? []).map((member) => [member.id, member]));
  const name = (memberId: string) => memberName(t, names.get(memberId));
  const loading = members.isPending || balances.isPending || plan.isPending;
  const failed = members.error ?? balances.error ?? plan.error;
  const currencies = balances.data?.currencies ?? [];
  const transfers = (plan.data?.currencies ?? []).flatMap((currency) =>
    currency.transfers.map((transfer) => ({ ...transfer, currency: currency.currency })),
  );

  return (
    <Card
      title={t('settlements.card')}
      action={
        showHistoryLink ? (
          <Link to="/expenses/settlements" className={textLink}>
            {t('settlements.seeAll')}
          </Link>
        ) : null
      }
    >
      {loading ? <LoadingState /> : null}
      {failed ? (
        <ErrorState
          message={errorMessage(t, failed)}
          onRetry={() => {
            void balances.refetch();
            void plan.refetch();
          }}
        />
      ) : null}
      {!loading && !failed && currencies.length === 0 ? (
        <EmptyState title={t('settlements.empty')} description={t('settlements.emptyHint')} />
      ) : null}
      {!loading && !failed
        ? currencies.map((currency) => (
            <ul
              key={currency.currency}
              aria-label={t(`currencies.${currency.currency}`)}
              className="divide-border flex flex-col divide-y"
            >
              {currency.members.map((member) => (
                <li key={member.memberId} className="flex items-center justify-between gap-3 py-2">
                  <span className="truncate">{name(member.memberId)}</span>
                  <span
                    className={`shrink-0 text-right font-semibold tabular-nums ${
                      member.direction === 'RECEIVES'
                        ? 'text-success'
                        : member.direction === 'OWES'
                          ? 'text-accent'
                          : 'text-muted'
                    }`}
                  >
                    {member.direction === 'RECEIVES'
                      ? t('settlements.receives', {
                          amount: formatMoney(member.balanceCents, currency.currency),
                        })
                      : member.direction === 'OWES'
                        ? t('settlements.owes', {
                            amount: formatMoney(-member.balanceCents, currency.currency),
                          })
                        : t('settlements.settled')}
                  </span>
                </li>
              ))}
            </ul>
          ))
        : null}
      {!loading && !failed && currencies.length > 0 && transfers.length === 0 ? (
        <p className="text-success font-semibold">{t('settlements.allSettled')}</p>
      ) : null}
      {transfers.length > 0 ? (
        <div className="flex flex-col gap-2">
          <h3 className="text-card-title text-muted font-semibold">{t('settlements.suggested')}</h3>
          <ul className="flex flex-col gap-2">
            {transfers.map((transfer) => {
              const label = {
                from: name(transfer.fromMemberId),
                to: name(transfer.toMemberId),
                amount: formatMoney(transfer.amountCents, transfer.currency),
              };
              return (
                <li
                  key={`${transfer.currency}-${transfer.fromMemberId}-${transfer.toMemberId}`}
                  className="bg-surface-raised flex flex-wrap items-center justify-between gap-2 rounded-lg px-3 py-2"
                >
                  <span>{t('settlements.transfer', label)}</span>
                  {can('finance:write') ? (
                    <button
                      type="button"
                      className={secondaryButton}
                      aria-label={t('settlements.recordLabel', label)}
                      disabled={record.isPending}
                      onClick={() =>
                        record.mutate({
                          fromMemberId: transfer.fromMemberId,
                          toMemberId: transfer.toMemberId,
                          currency: transfer.currency,
                          amountCents: transfer.amountCents,
                        })
                      }
                    >
                      {t('settlements.record')}
                    </button>
                  ) : null}
                </li>
              );
            })}
          </ul>
        </div>
      ) : null}
      {record.isSuccess && !record.isPending ? (
        <p role="status" className="text-muted">
          {t('settlements.recorded')}
        </p>
      ) : null}
      {record.isError ? <FormAlert message={errorMessage(t, record.error)} /> : null}
    </Card>
  );
}
