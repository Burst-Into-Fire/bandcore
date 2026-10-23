# M3 · Security Hardening & Delivery

**Goal:** Close the remaining account security gaps, add the audit log foundation, harden HTTP, and put end-to-end tests and a staging environment in place before business modules start.

**Exit criteria:** password reset and rate limiting are live; security events and tenant audit events are recorded append-only; security headers are verified by tests; Playwright runs in CI; every merge to `main` deploys to staging automatically.

**Depends on milestones:** M2

---

## M3-01 · Implement password reset flow

**Labels:** type:security, area:backend, area:frontend, module:identity, priority:high
**Depends on:** M2-07

### Context

Password reset is a common account takeover path. Tokens must be short-lived and single use, and responses must not reveal whether an account exists.

### Scope

- [ ] Request reset on the central host: identical response and similar timing whether the email exists or not
- [ ] Token: 256-bit random, stored hashed, single use, expires in 30 minutes; a new request invalidates previous tokens
- [ ] Reset applies the password policy, revokes every refresh token family of the user and sends a confirmation email
- [ ] Frontend pages: request reset and set new password (pt-BR)

### Acceptance criteria

- After a reset, every previous session of the user stops working

### Tests required

- Integration: email received; reused or expired token rejected; old sessions invalid after reset; unknown email returns the same response and still exercises the hashing path
- Vitest: form validation and error states

---

## M3-02 · Add rate limiting and brute-force protection

**Labels:** type:security, area:backend, module:identity, priority:critical
**Depends on:** M2-07

### Context

Login, signup and token endpoints are targets for credential stuffing and enumeration. Limits must apply per IP and per account, without letting an attacker lock out a real user permanently.

### Scope

- [ ] Bucket4j limits per IP and per account/email on login, signup, verification resend, password reset and slug availability (invitations are added in M4-02)
- [ ] Progressive delay or temporary lock after repeated failures for an account (never a permanent lockout)
- [ ] 429 Problem Details with `Retry-After`
- [ ] Client IP taken only from the trusted proxy
- [ ] In-memory buckets for now, behind an interface that can switch to a shared store (PostgreSQL or Redis) when the API runs on more than one instance

### Acceptance criteria

- Documented limits per endpoint, enforced in all environments

### Tests required

- Integration: exceeding a limit → 429 with `Retry-After`; the per-account limit still applies when the IP changes; a spoofed `X-Forwarded-For` from an untrusted source is ignored; limits reset after the window (fixed `Clock`)

---

## M3-03 · Build append-only audit log foundation

**Labels:** type:feature, area:backend, module:audit, priority:high
**Depends on:** M2-09

### Context

Money and stock changes need a trail that explains who did what and when. The trail must be impossible to edit from the application, and every module must be able to record into it within its own transaction.

### Scope

- [ ] Tenant table `audit_events` with RLS: `id`, `band_id`, `occurred_at`, `actor_user_id`, `action` (for example `expense.created`), `entity_type`, `entity_id`, `changes` (JSONB diff without unnecessary personal data), `request_id`, IP, user agent
- [ ] Global table `security_events` (owned by `identity`) for login success and failure, refresh token reuse, password reset, signup
- [ ] Grants: `bandhub_app` has only `INSERT` and `SELECT` on both tables
- [ ] `AuditRecorder` API in the `audit` module, called by other modules in the same transaction as the change
- [ ] Retention period for IP and user agent documented (feeds the LGPD record in M10-01)

### Acceptance criteria

- Audit records are written atomically with the business change and cannot be modified by the application

### Tests required

- Integration: `UPDATE` and `DELETE` on audit tables as `bandhub_app` → permission denied; a business failure rolls back its audit record; audit events are isolated between bands; a failed login is recorded without the password

---

## M3-04 · Harden the HTTP layer

**Labels:** type:security, area:backend, area:infra, priority:high
**Depends on:** M1-07, M2-07

### Context

Security headers and strict defaults reduce the impact of XSS, clickjacking and information leaks.

### Scope

- [ ] Headers on nginx and API responses: CSP (`default-src 'self'; script-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`), HSTS in production, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, minimal `Permissions-Policy`
- [ ] No CORS configuration (same-origin only)
- [ ] `Cache-Control: no-store` on API responses
- [ ] Request size limits; only `application/json` accepted on JSON endpoints
- [ ] Swagger UI off in production; actuator only on the management port
- [ ] Error responses never include stack traces, SQL or class names

### Acceptance criteria

- Headers are present on every response in every environment

### Tests required

- Integration: headers asserted on API responses; a CORS preflight from a foreign origin gets no `Access-Control-Allow-Origin`; oversized and wrong content type requests rejected
- Compose smoke test checks nginx headers with `curl`

---

## M3-05 · Set up Playwright E2E tests against docker-compose

**Labels:** type:test, area:frontend, area:infra, priority:high
**Depends on:** M2-12, M1-08

### Context

End-to-end tests are reserved for critical journeys. The first one covers onboarding and proves in a real browser that sessions do not cross subdomains.

### Scope

- [ ] Playwright project running against the docker-compose stack in CI (job after the image build)
- [ ] Mailpit API helper to read verification and invitation emails
- [ ] Data seeding through the API
- [ ] Journey: sign up → verify → create band → log in on `slug.localhost` → logout
- [ ] Isolation journey: logged in to band A, opening band B's host shows the login page (host-only cookie), and API calls with band A's token on band B's host fail
- [ ] Traces and videos uploaded as artifacts on failure

### Acceptance criteria

- The E2E job runs on pull requests and is a required check; total time under 10 minutes

### Tests required

- The journeys above

---

## M3-06 · Set up staging environment with continuous deploy from main

**Labels:** type:chore, area:infra, priority:high
**Depends on:** M1-10, M3-04

### Context

A staging environment on the real platform catches problems with wildcard domains, proxies and managed PostgreSQL early, and is where security scans and load tests run later.

### Scope

- [ ] Staging on the chosen platform: web, API and managed PostgreSQL with the owner and app roles
- [ ] Wildcard domain (for example `*.staging.<domain>`) with TLS
- [ ] Secrets in the platform's secret store
- [ ] Deploy on merge to `main` after CI passes; Flyway runs with owner credentials, separate from the runtime credentials
- [ ] Post-deploy smoke test (health and login page)
- [ ] Demo band seeded with fake data; no real personal data in staging

### Acceptance criteria

- A merge to `main` updates staging automatically, and a failing smoke test marks the deploy as failed

### Tests required

- Post-deploy smoke test
