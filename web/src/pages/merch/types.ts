import type { TFunction } from 'i18next';

import type { components } from '../../api/schema';
import { formatMoney, type Currency } from '../../lib/money';

export type Product = components['schemas']['ProductResponse'];
export type Variant = components['schemas']['ProductVariantResponse'];
export type Batch = components['schemas']['BatchResponse'];
export type Movement = components['schemas']['StockMovementResponse'];

/** Every merch query, refreshed when stock or the catalog changes. */
export const MERCH_QUERIES = [['products'], ['batches'], ['movements']] as const;

/** "M · Preta", or null for a product's default variant (neither size nor color). */
export function variantAttributes(variant: Pick<Variant, 'size' | 'color'>): string | null {
  const parts = [variant.size, variant.color].filter((part): part is string => Boolean(part));
  return parts.length ? parts.join(' · ') : null;
}

/** "Camiseta preta · M", or just the product's name for its default variant. */
export function variantName(productName: string, variant: Pick<Variant, 'size' | 'color'>): string {
  const attributes = variantAttributes(variant);
  return attributes ? `${productName} · ${attributes}` : productName;
}

/** The stock badge: out of stock, low stock, or nothing to say. */
export function stockAlert(t: TFunction, variant: Variant): string | null {
  if (variant.quantityOnHand === 0) {
    return t('stock.out');
  }
  return variant.lowStock ? t('stock.low') : null;
}

/**
 * "Custo médio R$ 25,00", or "Sem custo ainda" before the first batch. The API sends null for a
 * variant without an average, so both null and a missing value mean "no cost yet".
 */
export function averageCostLabel(
  t: TFunction,
  variant: Pick<Variant, 'averageUnitCostCents'>,
  currency: Currency,
): string {
  const cost: unknown = variant.averageUnitCostCents;
  return typeof cost === 'number'
    ? t('merch.averageCost', { amount: formatMoney(cost, currency) })
    : t('merch.noCost');
}
