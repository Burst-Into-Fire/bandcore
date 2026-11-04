import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { api } from '../../api/client';
import { unwrap } from '../../api/problem';
import { useBandAccess } from '../../auth/useBandAccess';
import { primaryButton, secondaryButton, textLink } from '../../components/buttons';
import { Card } from '../../components/Card';
import { Confirm } from '../../components/Confirm';
import { FormAlert } from '../../components/FormAlert';
import { PageHeader } from '../../components/PageHeader';
import { SelectField } from '../../components/SelectField';
import { EmptyState, ErrorState, LoadingState } from '../../components/states';
import { toLocalDate } from '../../lib/dates';
import { formatMoney } from '../../lib/money';
import { errorMessage } from '../errorMessage';
import { ExpenseTabs } from '../expenses/ExpenseTabs';
import { memberName } from '../expenses/splitState';
import { dayMonth, type Member } from '../expenses/types';
import { SettlementCard } from './SettlementCard';
import { SettlementForm } from './SettlementForm';
import { MONEY_QUERIES, type Settlement } from './types';

const PAGE_SIZE = 50;

type Pending = { id: string; action: 'edit' | 'delete' } | null;

/** Action names (not UI text). */
const EDIT = 'edit';
const DELETE = 'delete';

/**
 * Acertos: who receives and who owes, the suggested transfers, and the history of settlement
 * payments, which members record, edit and delete as the API allows.
 */
export function SettlementsPage() {
  const { t } = useTranslation();
  const { can, me } = useBandAccess();
  const queryClient = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [pending, setPending] = useState<Pending>(null);
  const [memberId, setMemberId] = useState('');
  const [page, setPage] = useState(0);
  const band = useQuery({
    queryKey: ['band'],
    queryFn: async () => unwrap(await api.GET('/band')),
  });
  const members = useQuery({
    queryKey: ['members'],
    queryFn: async () => unwrap(await api.GET('/members')),
  });
  const settlements = useQuery({
    queryKey: ['settlements', memberId, page],
    queryFn: async () =>
      unwrap(
        await api.GET('/settlements', {
          params: { query: { ...(memberId ? { memberId } : {}), page, size: PAGE_SIZE } },
        }),
      ),
    placeholderData: keepPreviousData,
  });
  const remove = useMutation({
    mutationFn: async (settlement: Settlement) =>
      unwrap(
        await api.DELETE('/settlements/{settlementId}', {
          params: {
            path: { settlementId: settlement.id },
            query: { version: settlement.version },
          },
          parseAs: 'text',
        }),
      ),
    onSuccess: async () => {
      setPending(null);
      await Promise.all(
        MONEY_QUERIES.map((queryKey) => queryClient.invalidateQueries({ queryKey: [...queryKey] })),
      );
    },
  });

  const names = new Map<string, Member>((members.data ?? []).map((member) => [member.id, member]));
  const name = (id: string) => memberName(t, names.get(id));
  const ownMemberId = members.data?.find((member) => member.userId === me.data?.id)?.id;
  const today = toLocalDate(new Date(), band.data?.timezone);
  const formProps =
    band.data && members.data
      ? {
          members: members.data,
          defaultCurrency: band.data.currency,
          ownMemberId,
          today,
        }
      : null;

  return (
    <>
      <PageHeader
        title={t('settlements.title')}
        subtitle={t('settlements.subtitle')}
        action={
          can('finance:write') && !adding ? (
            <button type="button" className={primaryButton} onClick={() => setAdding(true)}>
              {t('settlements.new')}
            </button>
          ) : null
        }
      />
      <ExpenseTabs />
      {adding && formProps ? (
        <SettlementForm {...formProps} onDone={() => setAdding(false)} />
      ) : null}
      <SettlementCard />
      <Card title={t('settlements.history')}>
        <div className="max-w-xs">
          <SelectField
            label={t('settlements.member')}
            value={memberId}
            onChange={(event) => {
              setMemberId(event.target.value);
              setPage(0);
            }}
            options={[
              { value: '', label: t('settlements.allMembers') },
              ...(members.data ?? []).map((member) => ({
                value: member.id,
                label: memberName(t, member),
              })),
            ]}
          />
        </div>
        {settlements.isPending ? <LoadingState /> : null}
        {settlements.isError ? (
          <ErrorState
            message={errorMessage(t, settlements.error)}
            onRetry={() => void settlements.refetch()}
          />
        ) : null}
        {settlements.data?.items.length === 0 ? (
          <EmptyState title={t('settlements.historyEmpty')} />
        ) : null}
        {remove.isError ? <FormAlert message={errorMessage(t, remove.error)} /> : null}
        <ul className="divide-border flex flex-col divide-y">
          {(settlements.data?.items ?? []).map((settlement) => (
            <li key={settlement.id} className="flex flex-col gap-2 py-2">
              {pending?.id === settlement.id && pending.action === EDIT && formProps ? (
                <SettlementForm
                  {...formProps}
                  settlement={settlement}
                  onDone={() => setPending(null)}
                />
              ) : (
                <div className="flex items-start justify-between gap-3">
                  <div className="flex min-w-0 flex-col">
                    <span className="truncate">
                      {t('settlements.paid', {
                        from: name(settlement.fromMemberId),
                        to: name(settlement.toMemberId),
                      })}
                    </span>
                    <span className="text-caption text-muted truncate">
                      {[dayMonth(settlement.paidOn), settlement.note].filter(Boolean).join(' · ')}
                    </span>
                  </div>
                  <div className="flex shrink-0 flex-col items-end gap-1">
                    <span className="font-semibold tabular-nums">
                      {formatMoney(settlement.amountCents, settlement.currency)}
                    </span>
                    <span className="flex gap-3">
                      {settlement.access.edit ? (
                        <button
                          type="button"
                          className={textLink}
                          onClick={() => setPending({ id: settlement.id, action: EDIT })}
                        >
                          {t('settlements.edit')}
                        </button>
                      ) : null}
                      {settlement.access.delete ? (
                        <button
                          type="button"
                          className={textLink}
                          onClick={() => setPending({ id: settlement.id, action: DELETE })}
                        >
                          {t('settlements.delete')}
                        </button>
                      ) : null}
                    </span>
                  </div>
                </div>
              )}
              {pending?.id === settlement.id && pending.action === DELETE ? (
                <Confirm
                  question={t('settlements.confirmDelete')}
                  pending={remove.isPending}
                  onConfirm={() => remove.mutate(settlement)}
                  onCancel={() => setPending(null)}
                />
              ) : null}
            </li>
          ))}
        </ul>
        {settlements.data && settlements.data.totalPages > 1 ? (
          <div className="flex items-center justify-between gap-3">
            <button
              type="button"
              className={secondaryButton}
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
            >
              {t('expenses.previousPage')}
            </button>
            <span className="text-muted">
              {t('expenses.pageOf', { page: page + 1, total: settlements.data.totalPages })}
            </span>
            <button
              type="button"
              className={secondaryButton}
              disabled={page + 1 >= settlements.data.totalPages}
              onClick={() => setPage(page + 1)}
            >
              {t('expenses.nextPage')}
            </button>
          </div>
        ) : null}
      </Card>
    </>
  );
}
