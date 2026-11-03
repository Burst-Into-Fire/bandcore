# ADR-0006: Money as integer minor units (BIGINT cents)

- **Status:** Accepted. BRL and USD are both supported from M5 (decided 2026-11-03, see [open points](../roadmap.md#open-points-with-the-requirements-docs))
- **Date:** 2026-10-29
- **Related:** RNF08, RN01, RN08, [M5-01](../roadmap/M05-expenses-splits.md)

## Context

Floating point cannot represent most decimal amounts exactly (`0.1 + 0.2 != 0.3`). Splits must always add up to the total (RN01), and balances must always sum to zero.

## Decision

- Amounts are stored as `BIGINT` in the smallest unit of the currency (cents), next to an ISO 4217 currency code (`BRL` or `USD`, both with two decimal places). Each band has a default currency that pre-fills forms (RF40); every amount keeps its own.
- In Java, a `Money` value object (`shared.money.Money`, M5-01) holds the amount and currency and does all arithmetic with overflow checks. `shared.money.Allocation` splits amounts with the largest remainder method; leftover cents go to the largest remainders and ties to the lower index, so parts always sum to the total and the same input always gives the same output.
- The web app runs the same algorithm (`web/src/lib/allocation.ts`). Both sides are tested against the shared vectors in `contracts/money/allocation.json`, so they agree on every cent.
- In TypeScript, amounts are integer `number`s of cents (safe up to 2^53). Formatting and parsing never go through floating point (`web/src/lib/money.ts`).
- `float` and `double` are forbidden in `shared.money` and the finance and merch packages (ArchUnit rule in `ArchitectureRulesTest`); `parseFloat` is forbidden in the web app (ESLint rule).
- Amounts in different currencies are never added together (RN08).

## Alternatives considered

- **`NUMERIC(12,2)` and `BigDecimal`.** Exact, but scale and rounding mode must be right everywhere, and JSON clients still tend to parse it as a float.
- **Floating point.** Rejected: rounding errors in money.

## Consequences

- The API exchanges amounts as integers (`amountCents`), so no client can introduce rounding.
- Display formatting (`R$ 3.500,00`) happens only at the edges.
