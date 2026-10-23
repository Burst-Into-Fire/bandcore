# M13 · Plans & Billing

**Goal:** Define plans, billing and platform administration from the README roadmap, so bandhub can be sold to other bands.

**Exit criteria:** plans, limits, payment provider and back-office requirements are agreed, and a follow-up set of issues exists for this milestone.

**Depends on milestones:** M10

---

## M13-01 · Discovery: plans and billing

**Labels:** type:spike, priority:normal
**Depends on:** M10-07

### Context

Self-service onboarding (M2-08) already creates bands. Charging for them requires plans with limits, a payment provider that works in Brazil, and an administration area for the platform operator.

### Scope

- [ ] Plans and limits (for example members, products, events, storage) and how they are enforced per tenant (entitlements in a new `billing` module)
- [ ] Payment provider for Brazil (for example Stripe, Asaas, Pagar.me or Mercado Pago), with PIX, card and boleto
- [ ] Subscription lifecycle: trial, renewal, failed payments, dunning, cancellation; band suspension using the `SUSPENDED` status from M2-02
- [ ] Platform back-office: separate admin host, mandatory MFA, every action audited, no RLS bypass (explicit per-band context only)
- [ ] Invoicing and tax obligations (NF-e)
- [ ] Output: the implementation issues for this milestone

### Acceptance criteria

- Issues for plans and billing are written in this file and reviewed

### Tests required

- None (discovery).
