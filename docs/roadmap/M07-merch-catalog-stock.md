# M7 · Merch Catalog & Stock

**Goal:** Deliver the first half of the merch module: products with variants, a stock ledger that can never go negative, production batches with unit cost, manual adjustments, and the integration that turns a batch cost into a split expense.

**Exit criteria:** an admin can register products and variants, receive a production batch (optionally creating a split expense), see stock per variant with low-stock warnings, and adjust inventory, with every stock change in the ledger.

**Depends on milestones:** M4 (and M5-01, M5-04 from the finance track)

---

## M7-01 · Manage products and variants

**Labels:** type:feature, area:backend, module:merch, priority:high, needs-discussion
**Depends on:** M4-01

### Context

Merch items such as "Camiseta preta" come in variants (M, G, GG). Stock and price live on the variant.

### Currency

Decided 2026-11-03: BRL and USD from the start (RF40, RNF08, RN08). Every amount stores its own currency, the band's default currency only pre-fills forms, and amounts in different currencies are never added together: totals and balances are computed per currency.

### Unit cost and product currency

Decided 2026-11-04 ([ADR-0009](../adr/0009-cogs-weighted-average-unit-cost.md)): the unit cost is not entered on the product; it is the weighted average of production batches (M7-03). A product has a single currency for its prices and cost, pre-filled with the band's default currency; it can change only while none of its variants has stock movements.

### Scope

- [ ] `products` tenant table: name (unique per band), description, currency, `default_price_cents`, archived flag
- [ ] `product_variants` tenant table: `product_id`, optional `size` and `color` columns (free text, unique per product), optional SKU (unique per band when set), optional `price_cents` override, `low_stock_threshold`, archived flag. A product without variations gets one default variant (no size, no color)
- [ ] Admins manage the catalog; products and variants with history are archived instead of deleted
- [ ] Listing for sales screens (active only) and for the catalog (all)

### Acceptance criteria

- The catalog from the mockup (black T-shirt M, G, GG and a patch) can be registered

### Tests required

- Integration: CRUD; SKU unique per band but reusable in another band; negative price → 400; archived variants leave the active listing (selling them is refused in M8-02); authorization matrix and isolation entries; audit events

---

## M7-02 · Implement stock ledger with non-negative guarantee

**Labels:** type:feature, area:backend, area:database, module:merch, priority:critical
**Depends on:** M7-01

### Context

Stock must never go negative, even when several phones sell the last unit at the same time. Every change must also be traceable.

### Scope

- [ ] `stock_levels` tenant table: `variant_id`, `quantity_on_hand` with `CHECK (quantity_on_hand >= 0)`
- [ ] `stock_movements` append-only tenant table: `variant_id`, `type` (`BATCH_IN`, `BATCH_REVERSAL`, `SALE`, `REFUND`, `ADJUSTMENT`, `RECONCILIATION`), `quantity_delta`, quantity after the movement, reference to the source, actor, `occurred_at`; `bandcore_app` has only `INSERT` and `SELECT`
- [ ] A single module-internal `StockService` through which every change passes: atomic conditional update (`… SET quantity_on_hand = quantity_on_hand + :delta WHERE … AND quantity_on_hand + :delta >= 0`); zero rows updated means insufficient stock; the movement is inserted in the same transaction
- [ ] Consistency check: `quantity_on_hand` equals the sum of movements for every variant

### Acceptance criteria

- Under any concurrency, stock never goes below zero and the ledger always explains the current quantity

### Tests required

- Integration: 20 threads each selling one unit of a variant with 5 units → exactly 5 succeed, quantity ends at 0 with 5 `SALE` movements; a direct SQL update to a negative value is rejected by the `CHECK`; consistency check passes after mixed operations; `UPDATE` or `DELETE` on movements as `bandcore_app` → denied; isolation entry

---

## M7-03 · Register production batches with weighted average unit cost

**Labels:** type:feature, area:backend, module:merch, priority:high, needs-discussion
**Depends on:** M7-02, M5-01

### Context

Profit uses the cost of what was sold (COGS). Each batch brings stock in at a cost, and the variant's unit cost is the weighted average of what is in stock.

### Decision

Decided 2026-11-04 ([ADR-0009](../adr/0009-cogs-weighted-average-unit-cost.md)): the unit cost is the weighted average of production batches; merch the band already has enters as a batch. Batch quantities and costs are immutable; supplier, order date and notes can be edited. A batch can be cancelled only while it is the latest movement of every variant it touched, which restores quantity and average exactly.

### Scope

- [ ] `production_batches` tenant table: supplier, ordered and received dates, currency, `total_cost_cents`, notes, status (`RECEIVED`, `CANCELLED`)
- [ ] `batch_items`: variant, quantity, line cost (unit cost × quantity when entered per item, or allocated from the total by quantity with `Allocation`), quantity and average before the batch
- [ ] Receiving a batch creates `BATCH_IN` movements; cancelling it creates `BATCH_REVERSAL` movements
- [ ] Weighted average cost per variant: `new_avg = (qty_on_hand × avg + line_cost) / (qty_on_hand + qty_in)`, rounded half-up to the cent (documented)
- [ ] Every item is in the batch's currency, which must be its product's currency
- [ ] Admins only

### Acceptance criteria

- After each batch, stock and average unit cost per variant are correct to the cent

### Tests required

- Unit: weighted average rounding cases
- Property-based (jqwik): the average always lies between the minimum and maximum input costs
- Integration: a batch increases stock and sets cost; a second batch with a different cost updates the average; allocated item costs sum exactly to the batch total; cancelling the latest batch restores quantity and average, cancelling an older one → 409; isolation entry; audit events

---

## M7-04 · Register batch cost as a split expense

**Labels:** type:feature, area:backend, module:merch, module:finance, priority:high
**Depends on:** M7-03, M5-04

### Context

This is where the two modules meet: the cost of a merch batch can become an expense split among the members. The modules stay decoupled through an application event.

### Decision

Decided 2026-11-04: the listener is synchronous and runs in the batch's transaction, like `BandCreated` → default categories. The batch and its expense are saved together or not at all, an invalid split answers 422 right away, and the listener runs with the admin's tenant and identity. The Spring Modulith event publication registry is left for the first truly asynchronous integration.

### Scope

- [ ] Optional "create expense" on batch registration, with description, category (the UI pre-selects the default merch category), payer, status (planned or paid) and split configuration
- [ ] `merch` publishes `ProductionBatchRegistered` (with `band_id`); `finance` listens in the same transaction and creates the expense, linked to the batch (`source_type = BATCH`, `source_id`, unique per band)
- [ ] Finance errors (archived category, inactive payer, invalid split) roll back the batch and point at the `expense.*` fields
- [ ] Rule for later edits: cancelling a batch never changes its expense; the UI warns and an admin updates the expense

### Acceptance criteria

- Registering a batch with "create expense" produces exactly one correctly split expense in the same band

### Tests required

- Integration: batch with expense → expense and splits created, linked to the batch; invalid expense → 422 and neither batch, stock movement nor expense is saved; a second expense for the same source is refused; the expense is created in the right band (RLS confirms)
- Architecture: `merch` does not depend on `finance`

---

## M7-05 · Implement manual stock adjustments

**Labels:** type:feature, area:backend, module:merch, priority:normal
**Depends on:** M7-02

### Context

Physical counts drift: items get damaged, lost or given away. Adjustments fix the numbers while keeping the reason on record.

### Scope

- [ ] Admins record a counted quantity or a delta with a reason (`COUNT`, `DAMAGED`, `LOST`, `GIFT`, `OTHER`), creating an `ADJUSTMENT` movement
- [ ] Never below zero
- [ ] The weighted average cost does not change ([ADR-0009](../adr/0009-cogs-weighted-average-unit-cost.md))
- [ ] Audited

### Acceptance criteria

- Inventory can be corrected with a recorded reason and never goes negative

### Tests required

- Integration: adjusting to a count sets the exact quantity; a result below zero → 409; the average cost is unchanged; `MEMBER` → 403; audit event; isolation entry

---

## M7-06 · Build merch catalog and stock UI

**Labels:** type:feature, area:frontend, module:merch, priority:high
**Depends on:** M4-06, M7-03, M7-05

### Context

The *Merch* page is where admins manage the catalog and stock, including the *Estoque de merch* card from the mockup.

### Scope

- [ ] Products with variants and prices; product and variant forms
- [ ] *Estoque de merch* list: variant name and quantity (*12 un*), low stock in coral (*3 un | Estoque baixo*)
- [ ] Batch registration form: items grid, total cost, optional expense creation reusing the split editor from M5-06
- [ ] Stock adjustment dialog
- [ ] Movement history per variant

### Acceptance criteria

- The merch catalog and stock from the mockup can be managed entirely from the UI

### Tests required

- Vitest + MSW: low-stock indicator, batch cost allocation display
- E2E: registering a batch with an expense increases stock and the expense appears in *Gastos*
