# M5 · Expenses & Splits

**Goal:** Deliver the first half of the account management module: exact money handling, categories, planned and paid expenses, and equal or custom splits between members.

**Exit criteria:** members can record expenses with future dates, categories and status, choose who paid, and split them equally or by custom amounts or percentages, with every cent accounted for.

**Depends on milestones:** M4

---

## M5-01 · Implement Money value object and allocation algorithm

**Labels:** type:feature, area:backend, module:finance, priority:critical, needs-discussion
**Depends on:** M1-05

### Context

Every money rule in bandcore depends on splitting an amount into parts that always sum exactly to the total. The cent that does not divide evenly must always go to the same place for the same input.

### Open question

The requirements now include BRL and USD (RF40, RNF08, RN08), with totals and balances computed per currency. This issue was written for BRL only and needs a currency dimension once that is confirmed.

### Scope

- [ ] `Money` in `shared`: integer cents and currency, immutable, overflow-checked arithmetic (`Math.addExact` and similar)
- [ ] `Allocation.equal(total, n)` and `Allocation.byWeights(total, weights)` (percentages in basis points), using the largest remainder method with a deterministic tie-break (stable member order)
- [ ] API contract: money fields are integers named `*Cents`
- [ ] Shared test vectors (JSON with inputs and expected cents) stored in the repository and used by backend and frontend tests
- [ ] ArchUnit rule: no `float` or `double` in `shared`, `finance` and `merch`

### Acceptance criteria

- R$ 100,00 split by 3 always gives R$ 33,34 + R$ 33,33 + R$ 33,33

### Tests required

- Unit: table-driven cases from the shared vectors; overflow throws
- Property-based (jqwik): parts always sum to the total; equal parts differ by at most one cent; weighted parts are within one cent of the exact proportion; the same input always gives the same output; no negative parts for non-negative totals

---

## M5-02 · Manage expense categories

**Labels:** type:feature, area:backend, module:finance, priority:normal
**Depends on:** M4-01

### Context

Categories (rehearsal space, gear, video, photography, travel, merch) organize expenses and the reports built on them.

### Scope

- [ ] `expense_categories` tenant table with RLS: name, optional color, archived flag
- [ ] Default categories created by listening to `BandCreated`: rehearsal space, gear, video, photography, travel, merch, other (default names shown through i18n keys)
- [ ] Admins create, rename and archive; a category in use is archived, never deleted
- [ ] Unique name per band

### Acceptance criteria

- New bands start with the default categories, and admins can tailor the list

### Tests required

- Integration: defaults created with the band; duplicate name → 409; archiving a category in use keeps existing expenses valid; authorization matrix and isolation entries

---

## M5-03 · Create and manage expenses

**Labels:** type:feature, area:backend, module:finance, priority:critical, needs-discussion
**Depends on:** M5-01, M5-02

### Context

Expenses are the base of balances. Planned expenses (future dates) are tracked but only paid expenses affect balances.

### Open question

The requirements now include BRL and USD (RF40, RNF08, RN08), with totals and balances computed per currency. This issue was written for BRL only and needs a currency dimension once that is confirmed.

### Scope

- [ ] `expenses` tenant table: `description`, `category_id`, `amount_cents` (> 0), `expense_date` (future allowed), `status` (`PLANNED`, `PAID`), `paid_by_member_id` (required when `PAID`), `paid_at`, `notes`, `created_by`, `deleted_at`, `version`, timestamps; composite foreign keys to category and member
- [ ] Endpoints to create, read, update and delete
- [ ] List with filters (month, status, category, member, text), pagination and sorting
- [ ] Ownership: members edit and delete their own expenses, admins any expense
- [ ] Paid expenses are soft-deleted (excluded from balances, kept for audit); planned expenses can be deleted
- [ ] Optimistic locking: concurrent edits return 409
- [ ] Every change audited with a diff

### Acceptance criteria

- Expenses can be recorded as planned or paid, with the payer and the category, and every change is traceable

### Tests required

- Integration: planned expense with a future date; `PAID` without payer → 422; payer must be an active member of the same band; filters; concurrent edit → 409; category or member IDs from another band → rejected; authorization matrix (including "own only") and isolation entries; audit events

---

## M5-04 · Split expenses equally or with custom amounts or percentages

**Labels:** type:feature, area:backend, module:finance, priority:critical, needs-discussion
**Depends on:** M5-03

### Context

Splits decide who owes what. The stored shares must always sum exactly to the expense amount.

### Open question

The requirements now include BRL and USD (RF40, RNF08, RN08), with totals and balances computed per currency. This issue was written for BRL only and needs a currency dimension once that is confirmed.

### Scope

- [ ] `expense_splits` tenant table: `expense_id`, `member_id`, `share_cents`, with composite foreign keys
- [ ] Modes: `EQUAL` (selected members, default all active members), `CUSTOM_AMOUNTS` (must sum exactly to the total), `CUSTOM_PERCENTAGES` (basis points summing to 10000, converted with `Allocation`)
- [ ] Shares stored as a cents snapshot; mode and inputs stored so the split can be edited later
- [ ] Shares recalculated atomically when the amount or participants change
- [ ] Only active members can be added to a split; the payer does not have to be part of it

### Acceptance criteria

- For every saved expense, the sum of its shares equals its amount

### Tests required

- Unit: validation of each mode
- Integration: equal split among 3 with remainder cents; custom amounts that do not add up → 422 with the difference in the error; percentages produce exact cents; editing the amount recalculates shares; a member from another band → rejected by the composite foreign key; after any operation the sum of shares equals the amount

---

## M5-05 · Mark planned expenses as paid

**Labels:** type:feature, area:backend, module:finance, priority:high
**Depends on:** M5-04

### Context

Planned expenses (for example "Fotógrafo, ensaio de divulgação, previsto 25/10") become paid when someone pays them. That transition is what moves balances.

### Scope

- [ ] `POST /api/expenses/{id}/pay` with payer and payment date (`PLANNED` → `PAID`)
- [ ] Reverting `PAID` → `PLANNED` only by admins, audited
- [ ] Upcoming planned expenses endpoint (next N days) for the dashboard
- [ ] Overdue flag for planned expenses whose date has passed

### Acceptance criteria

- Planned expenses can be paid and appear as upcoming until then

### Tests required

- Integration: valid and invalid transitions (invalid → 409); revert only by admins; upcoming and overdue lists; audit events
- Balance effects are verified in M6-01

---

## M5-06 · Build expenses page and "New expense" flow

**Labels:** type:feature, area:frontend, module:finance, priority:high
**Depends on:** M4-06, M5-05

### Context

The *Gastos* page and the *Novo gasto* button in the header are the most used parts of the account module.

### Scope

- [ ] *Gastos* page: list grouped by month with filters; status badge (*Pago* muted, *Previsto dd/MM* in coral); amounts right-aligned
- [ ] *Novo gasto* form: description, category, amount (cents-safe input), date, status, payer, and a split editor (equal / custom amounts / percentages) with a live "remaining" indicator that blocks saving until it reaches zero
- [ ] Edit, delete and mark as paid, respecting permissions
- [ ] Full-screen form on mobile

### Acceptance criteria

- A member can record and split an expense from a phone in under a minute

### Tests required

- Vitest: split editor uses the shared test vectors from M5-01, so frontend and backend agree on every cent; amount input parsing; create flow with MSW
- E2E: an expense paid by one member and split among four appears in the list with the right shares
