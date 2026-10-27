# M10 · Production Readiness & LGPD

**Goal:** Make bandcore ready to be offered to other bands: LGPD rights (consent, export, deletion), the audit trail viewer, observability, off-platform backups with a tested restore, the production pipeline, a security review and load tests.

**Exit criteria:** production runs on the chosen platform with wildcard TLS; backups are restored successfully in a drill; users can export and delete their data; the security review has no open high or critical findings; load targets are met.

**Depends on milestones:** M6, M9

---

## M10-01 · Record consent for privacy policy and terms

**Labels:** type:feature, area:backend, area:frontend, module:lgpd, priority:high
**Depends on:** M2-06

### Context

LGPD requires transparency about what personal data is processed and why. Users must accept the current terms, and the acceptance must be provable.

### Scope

- [ ] Versioned privacy policy and terms of use in pt-BR, served by the app
- [ ] `consents` table: user, document, version, `accepted_at`, IP and user agent; acceptance required at signup
- [ ] Prompt to accept again when a document version changes
- [ ] Record of processing activities at `docs/lgpd/ropa.md`: data categories, purpose, legal basis (contract execution, legitimate interest), retention
- [ ] Contact channel for data subject requests
- [ ] Cookie notice: only strictly necessary cookies, documented, so no consent banner is needed

### Acceptance criteria

- Every active user has accepted the current versions of the documents

### Tests required

- Integration: signup without acceptance → 422; a new version requires acceptance before continuing; consent history cannot be changed

### Security notes

- The legal texts must be reviewed by a lawyer before launch

---

## M10-02 · Export personal data

**Labels:** type:feature, area:backend, module:lgpd, priority:normal
**Depends on:** M10-01

### Context

LGPD gives users the right to access their data. Users may belong to several bands, so the export spans bands, but it must never include other people's personal data.

### Scope

- [ ] Export requested on the central host; an asynchronous job builds a JSON file with profile, memberships, linked member records, expenses created or paid, shares, settlements, sales made, consents and security events
- [ ] Download through an authenticated, expiring link
- [ ] Audited and rate limited

### Acceptance criteria

- A user receives a complete export of their own data from every band they belong to

### Tests required

- Integration: the export includes the user's data from both bands and no other user's personal data; the link expires; another user cannot download it

---

## M10-03 · Delete account with anonymization

**Labels:** type:feature, area:backend, module:lgpd, priority:high
**Depends on:** M10-02, M4-03

### Context

Users can ask for deletion, but band finances must stay consistent: other members still need correct balances and history.

### Scope

- [ ] Account deletion: blocked while the user is the only admin of a band (transfer or delete the band first); email and name anonymized, password hash removed, sessions revoked
- [ ] Linked member records keep financial history with an anonymized display name (for example *Ex-integrante 3*)
- [ ] Band deletion by an admin: 30-day grace period, then every row of that band is deleted; backups expire through the retention policy
- [ ] Security event recorded without personal data

### Acceptance criteria

- After deletion no personal data of the user remains, and every band's numbers still add up

### Tests required

- Integration: the deleted user cannot log in; their personal data is gone from `users` and `members`; balances still sum to zero; band deletion removes only that band's rows and another band stays intact

---

## M10-04 · Build audit trail viewer

**Labels:** type:feature, area:backend, area:frontend, module:audit, priority:normal
**Depends on:** M3-03, M9-05

### Context

Admins need to see who changed what, especially for money and stock.

### Scope

- [ ] Admin page listing audit events, with filters (date, actor, entity type, action), a detail view with the diff, and pagination
- [ ] Checklist of commands that must be audited: expenses, splits, settlements, batches, stock adjustments, sale cancellations, refunds, reconciliations, membership and role changes, settings
- [ ] Test that every command on the checklist emits an audit event

### Acceptance criteria

- Every action on the checklist appears in the viewer

### Tests required

- Integration: admins only; isolation entry; the checklist test above

---

## M10-05 · Add observability

**Labels:** type:chore, area:infra, area:backend, priority:high
**Depends on:** M3-06

### Context

Before real bands rely on the app, the team needs to know when something breaks, without leaking personal data into logs or third-party tools.

### Scope

- [ ] Micrometer metrics: HTTP, JVM, connection pool, and business counters (sales, sync outcomes, pending reconciliations)
- [ ] Metrics exposed only on the management port or through the platform
- [ ] Error tracking (tool to be decided) with personal data scrubbing and no request bodies
- [ ] Structured logs with request id, band id (UUID, not name) and user id; no secrets, tokens, passwords or emails
- [ ] Uptime monitoring; alerts on error rate and failed deploys
- [ ] Log retention aligned with the LGPD record

### Acceptance criteria

- An error in staging produces an alert with enough context to debug, and no personal data

### Tests required

- Integration: log output for a failed login contains neither email nor password (captured log appender); the metrics endpoint is not reachable through the public proxy

---

## M10-06 · Set up off-platform database backups and restore drill

**Labels:** type:chore, area:infra, area:database, priority:critical
**Depends on:** M3-06

### Context

The README requires backups stored outside the hosting platform, so the project can survive losing the platform or move to a VPS.

### Scope

- [ ] Daily `pg_dump` (custom format) from the managed database, encrypted, uploaded to external S3-compatible storage (provider to be decided)
- [ ] Retention (for example 7 daily, 4 weekly, 6 monthly) and monitoring of the backup job
- [ ] Documented restore procedure, including recreating the owner and app roles
- [ ] Quarterly restore drill to a fresh PostgreSQL with an app smoke test
- [ ] Validate the README "Moving to a VPS" runbook end to end
- [ ] Document RPO and RTO

### Acceptance criteria

- A restore drill was executed and timed, and the runbook was followed successfully

### Tests required

- The restore drill itself, documented with results

---

## M10-07 · Set up production deployment pipeline

**Labels:** type:chore, area:infra, priority:critical
**Depends on:** M10-05, M10-06

### Context

Production needs a predictable, reversible release process.

### Scope

- [ ] Production environment on the chosen platform; product domain with wildcard DNS and TLS
- [ ] Promotion from staging to production (release tag or manual approval)
- [ ] Flyway with owner credentials; backward-compatible migrations (expand/contract) so a release can be rolled back
- [ ] Rollback procedure
- [ ] Post-deploy smoke tests
- [ ] Secret management and rotation procedure, including JWT key rotation with `kid`
- [ ] HSTS enabled

### Acceptance criteria

- A release reaches production through the pipeline, and a rollback has been rehearsed

### Tests required

- Post-deploy smoke tests; rollback rehearsal documented

---

## M10-08 · Run pre-launch security review

**Labels:** type:security, area:backend, area:frontend, priority:critical
**Depends on:** M10-07

### Context

Before onboarding other bands, the whole system is reviewed against a standard checklist and attacked the way a malicious band would.

### Scope

- [ ] OWASP ASVS level 2 checklist (authentication, sessions, access control, validation, cryptography, logging, data protection)
- [ ] Revisit the threat model from M2-01
- [ ] OWASP ZAP baseline and authenticated scans on staging
- [ ] Manual multi-tenant tests: IDOR across bands, host header attacks, token replay across subdomains, handoff code abuse, offline queue tampering, invitation token abuse, mass assignment of `band_id` or `role` in payloads
- [ ] Dependency audit
- [ ] Fix every finding or open an issue for it; report stored in `docs/security/`

### Acceptance criteria

- No open high or critical findings at launch

### Tests required

- Every confirmed finding gets a regression test

---

## M10-09 · Load test critical flows with k6

**Labels:** type:test, area:backend, priority:normal
**Depends on:** M3-06, M8-05, M9-03

### Context

RLS and per-request membership checks add work to every query. Load tests confirm the design holds with many bands and a busy show night.

### Scope

- [ ] k6 scripts against staging with many seeded bands (for example 500): concurrent quick sales during a show peak, offline sync bursts, dashboard summaries
- [ ] Targets: p95 below 300 ms for a sale and below 500 ms for the dashboard
- [ ] Check with `EXPLAIN` that queries under RLS use indexes starting with `band_id`
- [ ] Tune connection pool sizing

### Acceptance criteria

- Targets are met, or each gap has an issue with the measurements

### Tests required

- The k6 scripts, versioned in the repository
