# ADR-0009: Cost of goods sold with weighted average unit cost

- **Status:** Accepted (2026-11-04, before M7: the unit cost comes from production batches instead of being entered on the product as in RF21)
- **Date:** 2026-10-29
- **Related:** RF21, RF24, RF25, RN05, RN08, [M7-02, M7-03, M7-05](../roadmap/M07-merch-catalog-stock.md), [M8-02, M8-04](../roadmap/M08-sales-refunds-events.md)

## Context

Merch is produced in batches with different costs (100 shirts at R$ 30, then 50 at R$ 35). Profit per event and per product must stay correct even when stock is left over, and past profit must not change when costs change later (RN05).

## Decision

- Each production batch records, per variant, the quantity received and its cost. The variant's unit cost is the weighted average of the stock on hand and the units received:

  ```
  new_average = (quantity_on_hand × average + line_cost) / (quantity_on_hand + quantity_in)
  ```

  computed in integer cents and rounded half-up to the cent (ADR-0006). `line_cost` is the exact cost of the batch line: quantity × unit cost when costs are entered per item, or the line's share of the batch total when the total is allocated by quantity (`Allocation.byWeights`, so the lines add up to the total).
- A variant has no average until its first batch. Units that entered without a cost (through an adjustment before any batch) take the cost of the first batch: with no average, `new_average = line_cost / quantity_in`.
- Merch the band already has when it starts using bandcore enters as a batch (for example "Estoque inicial"), with its cost.
- Manual adjustments (M7-05) never change the average: units leave and enter at the current average. When stock reaches zero the last average is kept, and the next batch replaces it (`quantity_on_hand = 0`).
- Batch quantities and costs are immutable. A batch can be cancelled only while it is still the latest stock movement of every variant it touched: the batch line stores the quantity and the average from before it, so the cancellation restores both exactly. Otherwise the fix is a stock adjustment.
- A product has a single currency for its prices and its cost (RN08: amounts in different currencies are never combined). It is pre-filled with the band's default currency; every line of a batch is in the batch's currency, which must be the currency of each product in it.
- Each sold item stores a snapshot of the unit cost at the moment of the sale (M8-02); profit is price minus that snapshot.

## Alternatives considered

- **Unit cost typed on the product** (requirements docs, RF21). Simple, but wrong as soon as two batches have different costs.
- **FIFO / batch tracking per unit.** More precise, but needs stock per batch and is overkill for a band's merch table.
- **Editable batch costs with the average recomputed.** Exact only while nothing else moved after the batch; afterwards it can only approximate, which is why only cancellation of the latest batch is allowed.
- **Prices in several currencies per product.** Selling in a currency other than the cost's would leave profit undefined until exchange rates exist (RF41).

## Consequences

- Correct profit per event, per product and over time.
- A batch registered with a wrong cost must be cancelled (while it is the latest movement) and registered again; later on, the error stays in the average.
- A band that tours abroad and wants USD prices for a BRL product needs a separate product until exchange rates (RF41) exist.
