import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { api } from '../../api/client';
import { unwrap } from '../../api/problem';
import { useBandAccess } from '../../auth/useBandAccess';
import { primaryButton, textLink } from '../../components/buttons';
import { StatusBadge } from '../../components/Card';
import { Confirm } from '../../components/Confirm';
import { FormAlert } from '../../components/FormAlert';
import { ChevronDownIcon } from '../../components/icons';
import { PageHeader } from '../../components/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '../../components/states';
import { formatMoney } from '../../lib/money';
import { errorMessage } from '../errorMessage';
import { MerchTabs } from './MerchTabs';
import { ProductForm } from './ProductForm';
import { averageCostLabel, variantAttributes, type Product, type Variant } from './types';
import { VariantForm } from './VariantForm';

/** Between a variant's attributes and its SKU (not UI text). */
const SEPARATOR = ' · ';

type Editing =
  | { kind: 'new' }
  | { kind: 'product'; product: Product }
  | { kind: 'variant'; product: Product; variant?: Variant }
  | null;

/**
 * Merch, Produtos: the band's catalog with prices and variants. Everyone sees it; admins
 * register products, add and change variants, and archive what no longer sells.
 */
export function ProductsPage() {
  const { t } = useTranslation();
  const { can } = useBandAccess();
  const [editing, setEditing] = useState<Editing>(null);
  const band = useQuery({
    queryKey: ['band'],
    queryFn: async () => unwrap(await api.GET('/band')),
  });
  const products = useQuery({
    queryKey: ['products', 'ALL'],
    queryFn: async () =>
      unwrap(await api.GET('/products', { params: { query: { status: 'ALL' } } })),
  });
  const canManage = can('merch:manage');

  return (
    <>
      <PageHeader
        title={t('merch.title')}
        subtitle={t('merch.subtitle')}
        action={
          canManage && editing === null ? (
            <button
              type="button"
              className={primaryButton}
              onClick={() => setEditing({ kind: 'new' })}
            >
              {t('merch.newProduct')}
            </button>
          ) : null
        }
      />
      <MerchTabs />
      {editing && band.data ? (
        editing.kind === 'variant' ? (
          <VariantForm
            key={editing.variant?.id ?? `new-${editing.product.id}`}
            product={editing.product}
            {...(editing.variant ? { variant: editing.variant } : {})}
            onDone={() => setEditing(null)}
          />
        ) : (
          <ProductForm
            key={editing.kind === 'product' ? editing.product.id : 'new'}
            {...(editing.kind === 'product' ? { product: editing.product } : {})}
            defaultCurrency={band.data.currency}
            onDone={() => setEditing(null)}
          />
        )
      ) : null}
      {products.isPending ? <LoadingState /> : null}
      {products.isError ? (
        <ErrorState
          message={errorMessage(t, products.error)}
          onRetry={() => void products.refetch()}
        />
      ) : null}
      {products.data?.length === 0 ? (
        <EmptyState
          title={t('merch.empty')}
          {...(canManage ? { description: t('merch.emptyHint') } : {})}
        />
      ) : null}
      {products.data && products.data.length > 0 ? (
        <ul className="rounded-card border-border bg-surface divide-border divide-y border">
          {products.data.map((product) => (
            <ProductRow
              key={product.id}
              product={product}
              canManage={canManage}
              onEdit={setEditing}
            />
          ))}
        </ul>
      ) : null}
    </>
  );
}

type Change =
  | { kind: 'archiveProduct' | 'unarchiveProduct' | 'deleteProduct' }
  | { kind: 'archiveVariant' | 'unarchiveVariant' | 'deleteVariant'; variant: Variant };

function ProductRow({
  product,
  canManage,
  onEdit,
}: {
  product: Product;
  canManage: boolean;
  onEdit: (editing: Editing) => void;
}) {
  const { t } = useTranslation();
  const detailsId = useId();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [confirming, setConfirming] = useState<Change | null>(null);
  const summary = product.variants
    .map((variant) => variantAttributes(variant))
    .filter((attributes): attributes is string => attributes !== null);

  const change = useMutation({
    mutationFn: async (requested: Change) => {
      const productPath = { params: { path: { productId: product.id } } };
      switch (requested.kind) {
        case 'archiveProduct':
          return unwrap(await api.POST('/products/{productId}/archive', productPath));
        case 'unarchiveProduct':
          return unwrap(await api.POST('/products/{productId}/unarchive', productPath));
        case 'deleteProduct':
          return unwrap(
            await api.DELETE('/products/{productId}', {
              params: { path: { productId: product.id }, query: { version: product.version } },
              parseAs: 'text',
            }),
          );
        case 'archiveVariant':
          return unwrap(
            await api.POST('/variants/{variantId}/archive', {
              params: { path: { variantId: requested.variant.id } },
            }),
          );
        case 'unarchiveVariant':
          return unwrap(
            await api.POST('/variants/{variantId}/unarchive', {
              params: { path: { variantId: requested.variant.id } },
            }),
          );
        case 'deleteVariant':
          return unwrap(
            await api.DELETE('/variants/{variantId}', {
              params: {
                path: { variantId: requested.variant.id },
                query: { version: requested.variant.version },
              },
              parseAs: 'text',
            }),
          );
      }
    },
    onSuccess: async () => {
      setConfirming(null);
      await queryClient.invalidateQueries({ queryKey: ['products'] });
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
          <span className="truncate font-medium">{product.name}</span>
          <span className="text-caption text-muted truncate">
            {summary.length ? summary.join(', ') : t('merch.singleItem')}
          </span>
        </span>
        <span className="flex shrink-0 items-center gap-2">
          <span className="flex flex-col items-end gap-1">
            <span className="font-semibold tabular-nums">
              {formatMoney(product.defaultPriceCents, product.currency)}
            </span>
            {product.archived ? (
              <StatusBadge tone="neutral">{t('merch.archived')}</StatusBadge>
            ) : null}
          </span>
          <ChevronDownIcon
            className={`text-muted size-5 shrink-0 transition-transform ${open ? 'rotate-180' : ''}`}
          />
        </span>
      </button>
      {open ? (
        <div id={detailsId} className="bg-surface-raised flex flex-col gap-3 px-4 py-3">
          {product.description ? <p className="text-muted">{product.description}</p> : null}
          <ul aria-label={t('merch.variantsOf', { name: product.name })} className="flex flex-col">
            {product.variants.map((variant) => (
              <li
                key={variant.id}
                className="border-border flex flex-col gap-1 border-b py-2 last:border-b-0"
              >
                <div className="flex items-center justify-between gap-3">
                  <span className="min-w-0 truncate">
                    {[variantAttributes(variant) ?? t('merch.singleItem'), variant.sku]
                      .filter(Boolean)
                      .join(SEPARATOR)}
                  </span>
                  <span className="shrink-0 tabular-nums">
                    {formatMoney(variant.effectivePriceCents, product.currency)}
                  </span>
                </div>
                <div className="text-caption text-muted flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span>{t('stock.units', { count: variant.quantityOnHand })}</span>
                  <span>{averageCostLabel(t, variant, product.currency)}</span>
                  {variant.archived ? (
                    <StatusBadge tone="neutral">{t('merch.archived')}</StatusBadge>
                  ) : null}
                </div>
                {canManage ? (
                  <div className="flex flex-wrap gap-x-4 gap-y-1">
                    <button
                      type="button"
                      className={textLink}
                      onClick={() => onEdit({ kind: 'variant', product, variant })}
                    >
                      {t('merch.edit')}
                    </button>
                    <button
                      type="button"
                      className={textLink}
                      onClick={() =>
                        change.mutate({
                          kind: variant.archived ? 'unarchiveVariant' : 'archiveVariant',
                          variant,
                        })
                      }
                    >
                      {variant.archived ? t('merch.unarchive') : t('merch.archive')}
                    </button>
                    <button
                      type="button"
                      className={textLink}
                      onClick={() => setConfirming({ kind: 'deleteVariant', variant })}
                    >
                      {t('merch.delete')}
                    </button>
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
          {change.isError ? <FormAlert message={errorMessage(t, change.error)} /> : null}
          {confirming ? (
            <Confirm
              question={
                confirming.kind === 'deleteProduct'
                  ? t('merch.confirmDeleteProduct')
                  : t('merch.confirmDeleteVariant')
              }
              pending={change.isPending}
              onConfirm={() => change.mutate(confirming)}
              onCancel={() => setConfirming(null)}
            />
          ) : null}
          {canManage ? (
            <div className="flex flex-wrap gap-x-4 gap-y-2">
              <button
                type="button"
                className={textLink}
                onClick={() => onEdit({ kind: 'variant', product })}
              >
                {t('merch.addVariant')}
              </button>
              <button
                type="button"
                className={textLink}
                onClick={() => onEdit({ kind: 'product', product })}
              >
                {t('merch.edit')}
              </button>
              <button
                type="button"
                className={textLink}
                onClick={() =>
                  change.mutate({ kind: product.archived ? 'unarchiveProduct' : 'archiveProduct' })
                }
              >
                {product.archived ? t('merch.unarchive') : t('merch.archive')}
              </button>
              <button
                type="button"
                className={textLink}
                onClick={() => setConfirming({ kind: 'deleteProduct' })}
              >
                {t('merch.delete')}
              </button>
            </div>
          ) : null}
        </div>
      ) : null}
    </li>
  );
}
