# Audit log

Two append-only tables record who did what and when ([M3-03](../roadmap/M03-security-hardening-delivery.md)). The application can only add and read records: `bandcore_app` has `INSERT` and `SELECT` on them, and `UPDATE`, `DELETE` and `TRUNCATE` are revoked (`AuditLogIT` proves it).

| Table | Scope | Written by | What |
|---|---|---|---|
| `audit_events` | Tenant table, row-level security | Any module, through `AuditRecorder` (audit module) | Changes to a band's data: `band.*`, `member.*` (created, updated, deactivated, activated, deleted, linked, unlinked), `invitation.*` (created, resent, revoked, accepted), `membership.*`, `expense_category.*` (created, updated, archived, unarchived, deleted), `expense.*` (created, updated with a field diff, paid, payment_reverted, deleted, with `soft_deleted` true for paid expenses), `settlement.*` (created, updated with a field diff, deleted; settlements are always soft-deleted), `product.*` and `product_variant.*` (created, updated with a field diff, archived, unarchived, deleted), `production_batch.*` (registered with its items, updated with a field diff, cancelled; supplier and notes only as lengths); stock movements are their own append-only ledger; later sales |
| `security_events` | Global table, identity module | `SecurityEvents` (identity module) | Account events: signup, email verification, login success and failure, rate-limited logins, refresh token reuse, logout everywhere, password reset |

## Audit events

Call `AuditRecorder.record(action, entityType, entityId, changes)` inside the transaction that makes the change:

- The record is written in that transaction (propagation `MANDATORY`): a failed change rolls back its record, and calling it outside a transaction fails.
- The band is read from the transaction's row-level security context inside the `INSERT`, so a record can never be written to another band.
- The actor is the authenticated user; the request id, client address and user agent come from the current request.
- `action` is `entity.verb` in lowercase (`expense.created`); `changes` is a JSON object, for example `{"name": {"from": "A", "to": "B"}}` (use `AuditRecorder.change`).
- **Never put passwords, tokens or personal data the trail does not need in `changes`.** Prefer ids and amounts over names and emails. `AuditRecorder.diff(before, after)` builds the changes between two snapshots; free text such as expense notes is recorded as its length only.

## Security events

Each security event is committed in its own transaction (propagation `REQUIRES_NEW`), so a failed login is kept even though the login itself rolls back.

- A failed login records the user id when the account exists and the reason (`unknown_email`, `wrong_password`, `account_disabled`, `email_not_verified`, `no_membership`). The response stays the same generic error.
- Passwords and tokens are never recorded. The email of an unknown account is never recorded either; the client address is enough to spot credential stuffing.
- Unknown and existing accounts record the same events (signup, password reset request), so recording does not change the response time.
- The table has a `host` column instead of `band_id`: most events happen before any band is known, and the table must never be mistaken for band data.

## Retention

| Data | Kept for | Why |
|---|---|---|
| `ip_address` and `user_agent` in both tables | 6 months, then cleared | Marco Civil da Internet (Lei 12.965/2014, art. 15) requires application access records for 6 months; after that they are personal data with no purpose |
| `security_events` rows | 12 months | Investigating account takeovers and abuse |
| `audit_events` rows | While the band exists | The band's own financial history |

The purge job runs as a maintenance role, not as `bandcore_app` (which cannot update or delete these tables). It is added with the LGPD record of processing activities in [M10-01](../roadmap/M10-production-readiness-lgpd.md), which also reviews these periods.
