# ADR-0008: Offline sale conflicts become pending reconciliation

- **Status:** Proposed (to be validated with the band before M9)
- **Date:** 2026-10-29
- **Related:** RF32, RNF10, RNF13, RN03, [M9-03, M9-05](../roadmap/M09-offline-sales-pwa.md)

## Context

At venues with no signal, sales are recorded offline and synced later. Two phones can sell the last unit of the same variant. When the second sale arrives, stock would go negative, but the money and the product have already changed hands.

## Decision

- A synced sale that would make stock negative is stored with status `PENDING_RECONCILIATION` and does **not** change stock.
- An admin resolves it with either an inventory adjustment (the stock count was wrong) or a cancellation with refund.
- Stock never goes below zero (`CHECK (quantity >= 0)`), online or offline.

## Alternatives considered

- **Reject the sale.** Loses the record of a real sale; the revenue disappears from the reports.
- **Allow negative stock.** Breaks RN03 and every stock report.

## Consequences

- Event closing must list pending reconciliations and may require them to be resolved first.
- The dashboard needs a visible counter for pending items.
