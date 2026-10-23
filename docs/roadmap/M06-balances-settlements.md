# M6 · Balances & Settlements

**Goal:** Complete the account management module: running balance per member, settlement payments, a suggested settlement plan, and the finance part of the summary dashboard.

**Exit criteria:** the *Resumo* page shows the month's expenses (paid and planned), the member settlement card (*Recebe* / *Deve*) and the latest expenses, and suggested transfers can be recorded in one click.

**Depends on milestones:** M5

---

## M6-01 · Calculate member balances

**Labels:** type:feature, area:backend, module:finance, priority:critical
**Depends on:** M5-05

### Context

The balance answers "who owes whom". In the mockup, the vocalist paid R$ 2.700,00 split among four members, so the vocalist receives R$ 2.025,00 and each of the others owes R$ 675,00.

### Scope

- [ ] Balance per member = paid by the member − the member's shares + settlements sent − settlements received, counting only `PAID`, non-deleted expenses
- [ ] `GET /api/balances` with each member's balance and its direction (receives or owes)
- [ ] Single aggregate SQL query (no N+1), backed by indexes
- [ ] Inactive members included while their balance is not zero
- [ ] Optional "as of" date

### Acceptance criteria

- Balances always sum to zero and match the mockup scenario

### Tests required

- Unit: balance calculator
- Property-based (jqwik): for any generated set of expenses, splits and settlements, the balances sum to zero
- Integration: the mockup scenario (+R$ 2.025,00 and three times −R$ 675,00); planned and deleted expenses do not affect balances; balances only include the current band

---

## M6-02 · Record settlement payments between members

**Labels:** type:feature, area:backend, module:finance, priority:high
**Depends on:** M6-01

### Context

Members settle debts by paying each other. Recording those payments brings balances back to zero.

### Scope

- [ ] `settlements` tenant table: `from_member_id`, `to_member_id`, `amount_cents` (> 0), `paid_at`, `note`, `created_by`; `CHECK (from_member_id <> to_member_id)`; composite foreign keys
- [ ] Create, edit and delete (members their own, admins any), audited
- [ ] List with filters

### Acceptance criteria

- Recording a settlement updates both members' balances

### Tests required

- Integration: a settlement moves both balances and the total stays zero; `from = to` → 422; a member from another band → rejected; authorization matrix and isolation entries; audit events

---

## M6-03 · Suggest a settlement plan with minimum transfers

**Labels:** type:feature, area:backend, module:finance, priority:normal
**Depends on:** M6-02

### Context

Instead of everyone working out who pays whom, bandhub suggests a short list of transfers that settles everything.

### Scope

- [ ] Greedy algorithm matching the largest debtor with the largest creditor, producing at most n − 1 transfers, with deterministic ordering
- [ ] `GET /api/balances/settlement-plan`
- [ ] The frontend records a suggested transfer through M6-02

### Acceptance criteria

- Applying all suggested transfers zeroes every balance

### Tests required

- Property-based (jqwik): applying the plan zeroes every balance; at most n − 1 transfers; same input gives the same plan
- Integration: the mockup scenario returns three transfers of R$ 675,00 to the vocalist

---

## M6-04 · Provide monthly finance summary

**Labels:** type:feature, area:backend, module:finance, priority:high
**Depends on:** M6-01

### Context

The dashboard needs one fast call with the month's numbers, computed in the band's timezone.

### Scope

- [ ] `GET /api/summary/finance?month=YYYY-MM`: total of the month, paid, planned, totals by category, latest expenses and upcoming planned expenses
- [ ] Month boundaries computed in the band timezone for timestamp fields such as `paid_at`

### Acceptance criteria

- The summary returns the mockup numbers for the mockup data: total R$ 3.500,00, paid R$ 2.700,00, planned R$ 800,00

### Tests required

- Integration: mockup numbers; boundary case where `paid_at = 2026-11-01T01:00Z` belongs to October in `America/Sao_Paulo`; isolation entry

---

## M6-05 · Build balances and settlements UI

**Labels:** type:feature, area:frontend, module:finance, priority:high
**Depends on:** M4-06, M6-03

### Context

The *Acerto entre integrantes* card is where members see what they owe or will receive and settle up.

### Scope

- [ ] *Acerto entre integrantes* card: each member with *Recebe R$ X* or *Deve R$ X*
- [ ] Suggested transfers with a *Registrar pagamento* action
- [ ] Settlement history page; record and edit settlement form

### Acceptance criteria

- A member can settle a suggested transfer in one click

### Tests required

- Vitest + MSW: rendering of receives and owes; recording a suggested transfer
- E2E: after recording every suggested transfer, all balances show zero

---

## M6-06 · Build summary dashboard, finance section

**Labels:** type:feature, area:frontend, module:finance, priority:high
**Depends on:** M6-04, M6-05

### Context

The *Resumo* page is the first screen of the band and follows the mockup.

### Scope

- [ ] Title *Resumo de {mês}* with a month selector
- [ ] KPI *Gastos do mês*: total plus *Pago X | Previsto Y*
- [ ] *Acerto entre integrantes* card (M6-05)
- [ ] *Últimos gastos* list with status (*Pago* or *Previsto dd/MM* in coral)
- [ ] Placeholders for the merch cards until M8-07
- [ ] *Novo gasto* action in the header
- [ ] Cards stack on mobile

### Acceptance criteria

- With the mockup data, the finance part of the page matches the mockup

### Tests required

- Component tests with MSW fixtures that reproduce the mockup data
- Playwright screenshots at 360 and 1280 px
