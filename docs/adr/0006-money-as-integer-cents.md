# ADR-0006: Money as integer minor units (BIGINT cents)

- **Status:** Accepted (whether USD is supported from the start is still an open point, see [open points](../roadmap.md#open-points-with-the-requirements-docs))
- **Date:** 2026-10-29
- **Related:** RNF08, RN01, RN08, [M5-01](../roadmap/M05-expenses-splits.md)

## Context

Floating point cannot represent most decimal amounts exactly (`0.1 + 0.2 != 0.3`). Splits must always add up to the total (RN01), and balances must always sum to zero.

## Decision

- Amounts are stored as `BIGINT` in the smallest unit of the currency (cents), next to an ISO 4217 currency code where more than one currency is possible.
- In Java, a `Money` value object (M5-01) holds the amount and currency and does all arithmetic and allocation. Remainders are distributed deterministically, so parts always sum to the total.
- In TypeScript, amounts are integer `number`s of cents (safe up to 2^53). Formatting and parsing never go through floating point (`web/src/lib/money.ts`).
- `float` and `double` are forbidden in the finance and merch packages (ArchUnit rule in `ArchitectureRulesTest`); `parseFloat` is forbidden in the web app (ESLint rule).
- Amounts in different currencies are never added together (RN08).

## Alternatives considered

- **`NUMERIC(12,2)` and `BigDecimal`.** Exact, but scale and rounding mode must be right everywhere, and JSON clients still tend to parse it as a float.
- **Floating point.** Rejected: rounding errors in money.

## Consequences

- The API exchanges amounts as integers (`amountCents`), so no client can introduce rounding.
- Display formatting (`R$ 3.500,00`) happens only at the edges.
