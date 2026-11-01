# ADR-0002: Multi-tenancy with a shared schema, band_id and PostgreSQL RLS

- **Status:** Accepted (2026-10-30, before M2: RLS ships in M2 instead of *Later* as in RNF07)
- **Date:** 2026-10-29
- **Related:** RNF01, RNF07, [M1-04](../roadmap/M01-project-foundation.md), [M2-04, M2-05](../roadmap/M02-multitenancy-auth-core.md), [docs/DATABASE.md](../DATABASE.md)

## Context

Every band is a tenant and must never see or change another band's data (RNF01). The product targets many small bands, so per-tenant infrastructure would be expensive. A single forgotten `WHERE band_id = ?` in application code would leak financial data, which is the worst failure this product can have.

## Decision

- **Shared schema.** All bands live in the same tables. Every tenant table has `band_id NOT NULL`.
- **Application filter.** Repositories always filter by the current band, taken from the authenticated context, never from a client-controlled parameter.
- **Row-Level Security as a second barrier.** Every tenant table has RLS `ENABLE`d and `FORCE`d, with policies comparing `band_id` to `current_setting('app.band_id', true)`. The API sets it with `set_config('app.band_id', ..., true)` at the start of each transaction (transaction-local, so pooled connections never carry a previous tenant). With no tenant set, policies return nothing (fail closed).
- **Least-privilege roles.** The API connects as `bandcore_app` (no superuser, `NOBYPASSRLS`, owns nothing); Flyway runs as `bandcore_owner`. This is already in place since M1-04 and covered by `DatabaseRolesIT`.
- **Composite foreign keys** `(band_id, id)` so a child row cannot point to another band's parent; every tenant index starts with `band_id`.
- Cross-tenant access answers 404, never 403, so IDs of other bands cannot be probed.

## Alternatives considered

- **Database per tenant.** Strongest isolation, but migrations, connections and cost grow with every band.
- **Schema per tenant.** Same operational cost on migrations and connection pools; Flyway runs N times.
- **Application filter only (RLS later).** What the requirements docs propose for the MVP. Simpler, but one missed filter leaks data, and adding RLS to an existing schema later means auditing every query again.

## Consequences

- Defense in depth: a bug in a repository returns no rows instead of another band's rows.
- Every migration that creates a tenant table must follow the checklist in [docs/DATABASE.md](../DATABASE.md); a schema guard test (M2-05) enforces it.
- Background jobs and admin tasks must set the tenant explicitly, or run as a dedicated role.
- Only standard PostgreSQL features are used, so the design works on any managed PostgreSQL and on a VPS.
