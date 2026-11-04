import { allocateByWeights } from '../../lib/allocation';

/** What each line costs: quantity × unit cost, or its share of the total by quantity. */
export function lineCosts(values: {
  costMode: 'PER_ITEM' | 'ALLOCATED';
  totalCostCents: number;
  items: readonly { quantity: number; unitCostCents: number }[];
}): number[] {
  const quantities = values.items.map((item) =>
    Number.isInteger(item.quantity) && item.quantity > 0 ? item.quantity : 0,
  );
  if (values.costMode === 'ALLOCATED') {
    return quantities.some((quantity) => quantity > 0)
      ? allocateByWeights(values.totalCostCents, quantities)
      : quantities.map(() => 0);
  }
  return values.items.map((item, index) => (quantities[index] ?? 0) * item.unitCostCents);
}
