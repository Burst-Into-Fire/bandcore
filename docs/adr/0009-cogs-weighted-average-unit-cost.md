# ADR-0009: Cost of goods sold with weighted average unit cost

- **Status:** Proposed (the requirements enter the unit cost on the product, RF21; see [open points](../roadmap.md#open-points-with-the-requirements-docs))
- **Date:** 2026-10-29
- **Related:** RF21, RN05, [M7-03](../roadmap/M07-merch-catalog-stock.md), [M8-04](../roadmap/M08-sales-refunds-events.md)

## Context

Merch is produced in batches with different costs (100 shirts at R$ 30, then 50 at R$ 35). Profit per event and per product must stay correct even when stock is left over, and past profit must not change when costs change later (RN05).

## Decision

- Each production batch records quantity and total cost. The variant's unit cost is the weighted average of the stock on hand and the new batch.
- Each sold item stores a snapshot of the unit cost at the moment of the sale; profit is price minus that snapshot.
- Averages are computed in integer cents with deterministic rounding (ADR-0006).

## Alternatives considered

- **Unit cost typed on the product** (requirements docs). Simple, but wrong as soon as two batches have different costs.
- **FIFO / batch tracking per unit.** More precise, but needs stock per batch and is overkill for a band's merch table.

## Consequences

- Correct profit per event, per product and over time.
- Stock adjustments must decide at which cost units enter or leave (documented in M7-05).
