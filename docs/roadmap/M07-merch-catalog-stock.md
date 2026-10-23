# M7 · Merch Catalog & Stock

**Goal:** Deliver the first half of the merch module: products with variants, a stock ledger that can never go negative, production batches with unit cost, manual adjustments, and the integration that turns a batch cost into a split expense.

**Exit criteria:** an admin can register products and variants, receive a production batch (optionally creating a split expense), see stock per variant with low-stock warnings, and adjust inventory, with every stock change in the ledger.

**Depends on milestones:** M4 (and M5-01, M5-04 from the finance track)

---

## M7-01 · Manage products and variants

**Labels:** type:feature, area:backend, module:merch, priority:high
**Depends on:** M4-01

### Context

Merch items such as "Camiseta preta" come in variants (M, G, GG). Stock and price live on the variant.

### Scope

- [ ] `products` tenant table: name, description, `default_price_cents`, active flag
- [ ] `product_variants` tenant table: `product_id`, attributes (size, color; JSONB with validated keys or explicit columns, decided in the issue), SKU (unique per band), optional `price_cents` override, `low_stock_threshold`, active flag
- [ ] Admins manage the catalog; products and variants with history are archived instead of deleted
- [ ] Listing for sales screens (active only) and for the catalog (all)

### Acceptance criteria

- The catalog from the mockup (black T-shirt M, G, GG and a patch) can be registered

### Tests required

- Integration: CRUD; SKU unique per band but reusable in another band; negative price → 422; archived variant cannot be sold; authorization matrix and isolation entries; audit events

---

## M7-02 · Implement stock ledger with non-negative guarantee

**Labels:** type:feature, area:backend, area:database, module:merch, priority:critical
**Depends on:** M7-01

### Context

Stock must never go negative, even when several phones sell the last unit at the same time. Every change must also be traceable.

### Scope

- [ ] `stock_levels` tenant table: `variant_id`, `quantity_on_hand` with `CHECK (quantity_on_hand >= 0)`
- [ ] `stock_movements` append-only tenant table: `variant_id`, `type` (`BATCH_IN`, `SALE`, `REFUND`, `ADJUSTMENT`, `RECONCILIATION`), `quantity_delta`, reference to the source, actor, `occurred_at`; `bandhub_app` has only `INSERT` and `SELECT`
- [ ] A single module-internal `StockService` through which every change passes: atomic conditional update (`… SET quantity_on_hand = quantity_on_hand + :delta WHERE … AND quantity_on_hand + :delta >= 0`); zero rows updated means insufficient stock; the movement is inserted in the same transaction
- [ ] Consistency check: `quantity_on_hand` equals the sum of movements for every variant

### Acceptance criteria

- Under any concurrency, stock never goes below zero and the ledger always explains the current quantity

### Tests required

- Integration: 20 threads each selling one unit of a variant with 5 units → exactly 5 succeed, quantity ends at 0 with 5 `SALE` movements; a direct SQL update to a negative value is rejected by the `CHECK`; consistency check passes after mixed operations; `UPDATE` or `DELETE` on movements as `bandhub_app` → denied; isolation entry

---

## M7-03 · Register production batches with weighted average unit cost

**Labels:** type:feature, area:backend, module:merch, priority:high
**Depends on:** M7-02, M5-01

### Context

Profit uses the cost of what was sold (COGS). Each batch brings stock in at a cost, and the variant's unit cost is the weighted average of what is in stock.

### Scope

- [ ] `production_batches` tenant table: supplier, ordered and received dates, `total_cost_cents`, notes
- [ ] `batch_items`: variant, quantity, `unit_cost_cents` (entered per item, or allocated from the total by quantity with `Allocation`)
- [ ] Receiving a batch creates `BATCH_IN` movements
- [ ] Weighted average cost per variant: `new_avg = (qty_on_hand × avg + qty_in × unit_cost) / (qty_on_hand + qty_in)`, rounded half-up to the cent (documented)
- [ ] Admins only

### Acceptance criteria

- After each batch, stock and average unit cost per variant are correct to the cent

### Tests required

- Unit: weighted average rounding cases
- Property-based (jqwik): the average always lies between the minimum and maximum input costs
- Integration: a batch increases stock and sets cost; a second batch with a different cost updates the average; allocated item costs sum exactly to the batch total; isolation entry; audit events

---

## M7-04 · Register batch cost as a split expense

**Labels:** type:feature, area:backend, module:merch, module:finance, priority:high
**Depends on:** M7-03, M5-04

### Context

This is where the two modules meet: the cost of a merch batch can become an expense split among the members. The modules stay decoupled through an application event.

### Scope

- [ ] Optional "create expense" on batch registration, with payer, status (planned or paid) and split configuration
- [ ] `merch` publishes `ProductionBatchRegistered` (with `band_id`); `finance` listens and creates an expense in the merch category, linked to the batch (`source_type = BATCH`, `source_id`)
- [ ] Spring Modulith event publication registry, so the event is not lost if the listener fails; idempotent listener (unique on the source)
- [ ] The listener sets the tenant context explicitly from the event's `band_id`
- [ ] Rule for later edits: changing the batch cost does not silently change a paid expense; the UI warns and an admin updates the expense

### Acceptance criteria

- Registering a batch with "create expense" produces exactly one correctly split expense in the same band

### Tests required

- Integration (Spring Modulith `Scenario`): batch with expense → expense and splits created; listener failure → retried from the publication registry without duplicates; the expense is created in the right band (RLS confirms)
- Architecture: `merch` does not depend on `finance` internals

---

## M7-05 · Implement manual stock adjustments

**Labels:** type:feature, area:backend, module:merch, priority:normal
**Depends on:** M7-02

### Context

Physical counts drift: items get damaged, lost or given away. Adjustments fix the numbers while keeping the reason on record.

### Scope

- [ ] Admins record a counted quantity or a delta with a reason (`COUNT`, `DAMAGED`, `LOST`, `GIFT`, `OTHER`), creating an `ADJUSTMENT` movement
- [ ] Never below zero
- [ ] Audited

### Acceptance criteria

- Inventory can be corrected with a recorded reason and never goes negative

### Tests required

- Integration: adjusting to a count sets the exact quantity; a result below zero → 409; `MEMBER` → 403; audit event; isolation entry

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
