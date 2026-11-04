import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';

import { api } from '../../api/client';
import { unwrap } from '../../api/problem';
import { useBandAccess } from '../../auth/useBandAccess';
import { primaryButton, secondaryButton, textLink } from '../../components/buttons';
import { StatusBadge } from '../../components/Card';
import { Confirm } from '../../components/Confirm';
import { FormAlert } from '../../components/FormAlert';
import { ChevronDownIcon } from '../../components/icons';
import { PageHeader } from '../../components/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '../../components/states';
import { toLocalDate } from '../../lib/dates';
import { formatMoney } from '../../lib/money';
import { errorMessage } from '../errorMessage';
import { dayMonth } from '../expenses/types';
import { BatchForm } from './BatchForm';
import { MerchTabs } from './MerchTabs';
import { MERCH_QUERIES, variantName, type Batch } from './types';

const PAGE_SIZE = 20;

/**
 * Merch, Lotes: production batches, latest received first. Admins register a batch ("Novo
 * lote"), optionally with the expense that pays for it, and cancel the latest one of its
 * variants when it was a mistake.
 */
export function BatchesPage() {
  const { t } = useTranslation();
  const { can, me } = useBandAccess();
  const [params, setParams] = useSearchParams();
  const [adding, setAdding] = useState(false);
  const page = Number(params.get('page') ?? '0') || 0;
  const canManage = can('merch:manage');

  const band = useQuery({
    queryKey: ['band'],
    queryFn: async () => unwrap(await api.GET('/band')),
  });
  const products = useQuery({
    queryKey: ['products', 'ALL'],
    queryFn: async () =>
      unwrap(await api.GET('/products', { params: { query: { status: 'ALL' } } })),
  });
  const members = useQuery({
    queryKey: ['members'],
    queryFn: async () => unwrap(await api.GET('/members')),
    enabled: canManage,
  });
  const categories = useQuery({
    queryKey: ['expense-categories'],
    queryFn: async () => unwrap(await api.GET('/expense-categories')),
    enabled: canManage,
  });
  const batches = useQuery({
    queryKey: ['batches', page],
    queryFn: async () =>
      unwrap(await api.GET('/batches', { params: { query: { page, size: PAGE_SIZE } } })),
    placeholderData: keepPreviousData,
  });

  const formReady = band.data && products.data && members.data && categories.data;
  const ownMemberId = members.data?.find((member) => member.userId === me.data?.id)?.id;
  const setPage = (next: number) => {
    const updated = new URLSearchParams(params);
    if (next > 0) {
      updated.set('page', String(next));
    } else {
      updated.delete('page');
    }
    setParams(updated);
  };

  return (
    <>
      <PageHeader
        title={t('merch.title')}
        subtitle={t('merch.subtitle')}
        action={
          canManage && !adding ? (
            <button type="button" className={primaryButton} onClick={() => setAdding(true)}>
              {t('batches.new')}
            </button>
          ) : null
        }
      />
      <MerchTabs />
      {adding && formReady ? (
        <BatchForm
          products={products.data}
          members={members.data}
          categories={categories.data}
          defaultCurrency={band.data.currency}
          today={toLocalDate(new Date(), band.data.timezone)}
          ownMemberId={ownMemberId}
          onDone={() => setAdding(false)}
        />
      ) : null}
      {batches.isPending ? <LoadingState /> : null}
      {batches.isError ? (
        <ErrorState
          message={errorMessage(t, batches.error)}
          onRetry={() => void batches.refetch()}
        />
      ) : null}
      {batches.data?.items.length === 0 ? (
        <EmptyState
          title={t('batches.empty')}
          {...(canManage ? { description: t('batches.emptyHint') } : {})}
        />
      ) : null}
      {batches.data && batches.data.items.length > 0 ? (
        <ul className="rounded-card border-border bg-surface divide-border divide-y border">
          {batches.data.items.map((batch) => (
            <BatchRow key={batch.id} batch={batch} canManage={canManage} />
          ))}
        </ul>
      ) : null}
      {batches.data && batches.data.totalPages > 1 ? (
        <nav aria-label={t('expenses.pageOf', { page: page + 1, total: batches.data.totalPages })}>
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
              {t('expenses.pageOf', { page: page + 1, total: batches.data.totalPages })}
            </span>
            <button
              type="button"
              className={secondaryButton}
              disabled={page + 1 >= batches.data.totalPages}
              onClick={() => setPage(page + 1)}
            >
              {t('expenses.nextPage')}
            </button>
          </div>
        </nav>
      ) : null}
    </>
  );
}

function BatchRow({ batch, canManage }: { batch: Batch; canManage: boolean }) {
  const { t } = useTranslation();
  const detailsId = useId();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const cancelled = batch.status === 'CANCELLED';

  const cancel = useMutation({
    mutationFn: async () =>
      unwrap(
        await api.POST('/batches/{batchId}/cancel', {
          params: { path: { batchId: batch.id } },
          body: { version: batch.version },
        }),
      ),
    onSuccess: async () => {
      setConfirming(false);
      await Promise.all(
        MERCH_QUERIES.map((queryKey) => queryClient.invalidateQueries({ queryKey })),
      );
    },
  });

  return (
    <li className="flex flex-col">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={detailsId}
        onClick={() => setOpen(!open)}
        className="hover:bg-surface-raised flex w-full items-center justify-between gap-3 px-4 py-3 text-left"
      >
        <span className="flex min-w-0 flex-col">
          <span className="truncate font-medium">
            {batch.supplier ?? t('batches.received', { date: dayMonth(batch.receivedOn) })}
          </span>
          <span className="text-caption text-muted truncate">
            {[
              batch.supplier ? t('batches.received', { date: dayMonth(batch.receivedOn) }) : null,
              t('batches.itemCount', { count: batch.items.length }),
            ]
              .filter(Boolean)
              .join(' · ')}
          </span>
        </span>
        <span className="flex shrink-0 items-center gap-2">
          <span className="flex flex-col items-end gap-1">
            <span
              className={`font-semibold tabular-nums ${cancelled ? 'text-muted line-through' : ''}`}
            >
              {formatMoney(batch.totalCostCents, batch.currency)}
            </span>
            {cancelled ? (
              <StatusBadge tone="neutral">{t('batches.cancelled')}</StatusBadge>
            ) : batch.expenseRequested ? (
              <StatusBadge tone="accent">{t('batches.expenseCreated')}</StatusBadge>
            ) : null}
          </span>
          <ChevronDownIcon
            className={`text-muted size-5 shrink-0 transition-transform ${open ? 'rotate-180' : ''}`}
          />
        </span>
      </button>
      {open ? (
        <div id={detailsId} className="bg-surface-raised flex flex-col gap-3 px-4 py-3">
          <ul className="flex flex-col">
            {batch.items.map((item) => (
              <li key={item.id} className="flex justify-between gap-3 py-1">
                <span className="min-w-0 truncate">
                  {t('batches.line', {
                    quantity: item.quantity,
                    name: variantName(item.productName, item),
                  })}
                </span>
                <span className="shrink-0 tabular-nums">
                  {formatMoney(item.lineCostCents, batch.currency)}
                </span>
              </li>
            ))}
          </ul>
          {batch.notes ? <p className="text-muted">{batch.notes}</p> : null}
          {cancel.isError ? <FormAlert message={errorMessage(t, cancel.error)} /> : null}
          {canManage && !cancelled && !batch.cancellable ? (
            <p className="text-caption text-muted">{t('batches.notCancellable')}</p>
          ) : null}
          {canManage && !cancelled && batch.cancellable ? (
            confirming ? (
              <Confirm
                question={
                  batch.expenseRequested
                    ? t('batches.confirmCancelWithExpense')
                    : t('batches.confirmCancel')
                }
                pending={cancel.isPending}
                onConfirm={() => cancel.mutate()}
                onCancel={() => setConfirming(false)}
              />
            ) : (
              <button
                type="button"
                className={`${textLink} self-start`}
                onClick={() => setConfirming(true)}
              >
                {t('batches.cancelBatch')}
              </button>
            )
          ) : null}
        </div>
      ) : null}
    </li>
  );
}
