import { useQuery } from '@tanstack/react-query';
import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { api } from '../../api/client';
import { unwrap } from '../../api/problem';
import { useBandAccess } from '../../auth/useBandAccess';
import { secondaryButton } from '../../components/buttons';
import { Card, StatusBadge } from '../../components/Card';
import { ChevronDownIcon } from '../../components/icons';
import { PageHeader } from '../../components/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '../../components/states';
import { errorMessage } from '../errorMessage';
import { AdjustStockForm } from './AdjustStockForm';
import { MerchTabs } from './MerchTabs';
import { MovementHistory } from './MovementHistory';
import { averageCostLabel, stockAlert, variantName, type Product, type Variant } from './types';

interface StockLine {
  product: Product;
  variant: Variant;
  name: string;
}

/**
 * Merch, Estoque: how many units of each variant the band has ("12 un"), with low stock in
 * coral, the history of every movement, and stock adjustments for admins.
 */
export function StockPage() {
  const { t } = useTranslation();
  const { can } = useBandAccess();
  const [adjusting, setAdjusting] = useState<StockLine | null>(null);
  const band = useQuery({
    queryKey: ['band'],
    queryFn: async () => unwrap(await api.GET('/band')),
  });
  const products = useQuery({
    queryKey: ['products', 'ALL'],
    queryFn: async () =>
      unwrap(await api.GET('/products', { params: { query: { status: 'ALL' } } })),
  });
  const lines: StockLine[] = (products.data ?? [])
    .filter((product) => !product.archived)
    .flatMap((product) =>
      product.variants
        .filter((variant) => !variant.archived)
        .map((variant) => ({ product, variant, name: variantName(product.name, variant) })),
    );

  return (
    <>
      <PageHeader title={t('merch.title')} subtitle={t('merch.subtitle')} />
      <MerchTabs />
      {adjusting ? (
        <AdjustStockForm
          key={adjusting.variant.id}
          variantId={adjusting.variant.id}
          name={adjusting.name}
          quantityOnHand={adjusting.variant.quantityOnHand}
          onDone={() => setAdjusting(null)}
        />
      ) : null}
      {products.isPending ? <LoadingState /> : null}
      {products.isError ? (
        <ErrorState
          message={errorMessage(t, products.error)}
          onRetry={() => void products.refetch()}
        />
      ) : null}
      {products.data && lines.length === 0 ? (
        <EmptyState title={t('stock.empty')} description={t('stock.emptyHint')} />
      ) : null}
      {lines.length > 0 ? (
        <Card title={t('stock.card')}>
          <ul className="divide-border -mx-4 -mb-4 flex flex-col divide-y">
            {lines.map((line) => (
              <StockRow
                key={line.variant.id}
                line={line}
                timeZone={band.data?.timezone}
                onAdjust={can('merch:manage') ? () => setAdjusting(line) : undefined}
              />
            ))}
          </ul>
        </Card>
      ) : null}
    </>
  );
}

function StockRow({
  line,
  timeZone,
  onAdjust,
}: {
  line: StockLine;
  timeZone: string | undefined;
  onAdjust: (() => void) | undefined;
}) {
  const { t } = useTranslation();
  const detailsId = useId();
  const [open, setOpen] = useState(false);
  const { product, variant } = line;
  const alert = stockAlert(t, variant);

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
          <span className="truncate font-medium">{line.name}</span>
          <span className="text-caption text-muted truncate">
            {averageCostLabel(t, variant, product.currency)}
          </span>
        </span>
        <span className="flex shrink-0 items-center gap-2 whitespace-nowrap">
          <span className="flex flex-col items-end gap-1">
            <span className={`font-semibold tabular-nums ${alert ? 'text-warning' : ''}`}>
              {t('stock.units', { count: variant.quantityOnHand })}
            </span>
            {alert ? <StatusBadge tone="warning">{alert}</StatusBadge> : null}
          </span>
          <ChevronDownIcon
            className={`text-muted size-5 shrink-0 transition-transform ${open ? 'rotate-180' : ''}`}
          />
        </span>
      </button>
      {open ? (
        <div
          id={detailsId}
          aria-label={t('stock.details', { name: line.name })}
          role="region"
          className="bg-surface-raised flex flex-col gap-3 px-4 py-3"
        >
          <MovementHistory variantId={variant.id} timeZone={timeZone} />
          {onAdjust ? (
            <button type="button" className={`${secondaryButton} self-start`} onClick={onAdjust}>
              {t('stock.adjust')}
            </button>
          ) : null}
        </div>
      ) : null}
    </li>
  );
}
