# M2 · Multi-tenancy & Authentication Core

**Goal:** Build the security core before any business feature: bands as tenants, tenant resolution from the subdomain, PostgreSQL Row-Level Security, accounts, band-scoped login, roles, and the automated guards that prove one band can never reach another band's data.

**Exit criteria:** a user can sign up, verify the email, create a band and log in on the band's subdomain; the schema guard, authorization matrix and cross-tenant isolation harness run in CI and fail on any unprotected table or endpoint.

**Depends on milestones:** M1

---

## M2-01 · Write threat model for tenant isolation and authentication

**Labels:** type:security, priority:critical
**Depends on:** M1-02

### Context

Before building authentication and tenancy, the threats need to be listed explicitly, so every mitigation has an issue and a test behind it instead of relying on memory.

### Scope

- [ ] Data flow diagram: browser on a band subdomain → nginx → API → PostgreSQL; central host; email provider
- [ ] STRIDE analysis per element, covering at least:
  - cross-tenant access: IDOR with another band's IDs, `Host` header spoofing, tampered `band_id` claim, token replay across subdomains, a new table without RLS, background jobs without tenant context, tenant context leaking through the connection pool
  - account takeover: credential stuffing, leaked reset or verification tokens, stolen refresh token
  - enumeration of accounts and band slugs
  - CSRF on cookie-based endpoints; XSS stealing tokens
  - offline data left on shared phones
- [ ] Map each threat to its mitigation and to the issue that implements and tests it
- [ ] Store at `docs/security/threat-model.md`, with a short review checklist for pull requests touching authentication or tenancy

### Acceptance criteria

- Every high-risk threat has a mitigation linked to an issue that includes a test

### Tests required

- None (documentation). Reviewed again in M10-08.

---

## M2-02 · Create band entity with slug rules

**Labels:** type:feature, area:backend, module:tenancy, priority:critical
**Depends on:** M1-05

### Context

The band is the tenant. Its slug becomes the subdomain, so it has to be valid as a DNS label, unique regardless of case, and must not collide with platform hosts.

### Scope

- [ ] `bands` table: `id` (UUIDv7), `slug`, `name`, `currency` (default `BRL`), `timezone` (default `America/Sao_Paulo`), `status` (`ACTIVE`, `SUSPENDED`), timestamps
- [ ] UUIDv7 generator in `shared`
- [ ] Slug rules: 3 to 32 characters, lowercase letters, digits and single hyphens, starting and ending with a letter or digit, no `xn--` prefix; unique index on `lower(slug)`
- [ ] Reserved slugs kept in code (`app`, `api`, `www`, `admin`, `mail`, `static`, `assets`, `status`, `help`, `support`, `docs`, `staging`, `dev`, `auth`, `login`, `billing`, …)
- [ ] `bands` is a global table (it *is* the tenant), accessible only through the `tenancy` module (ArchUnit rule)

### Acceptance criteria

- Invalid, reserved and duplicate (case-insensitive) slugs are rejected with clear Problem Details error codes

### Tests required

- Unit: table-driven slug validator; property test that generated valid slugs are accepted and invalid characters are rejected
- Integration: case-insensitive uniqueness enforced by the database; two concurrent creations of the same slug result in exactly one band

---

## M2-03 · Resolve tenant from request host

**Labels:** type:security, area:backend, module:tenancy, priority:critical
**Depends on:** M2-02, M1-10

### Context

The subdomain decides which band a request belongs to. The tenant must come only from the validated host, never from the request body, query string or arbitrary headers.

### Scope

- [ ] `BASE_DOMAIN` configuration (`bandhub.app` in production, `localhost` in development); `app.{BASE_DOMAIN}` is the central host, `{slug}.{BASE_DOMAIN}` is a band host
- [ ] Resolve from `Host`; honor `X-Forwarded-Host` only from the configured trusted proxy
- [ ] Normalize the host (lowercase, strip port and trailing dot) before matching
- [ ] Immutable, request-scoped `TenantContext` holding band id and slug
- [ ] Unknown or malformed host returns 404; suspended band returns a generic 403
- [ ] Short-lived slug → band id cache, evicted when the band status changes
- [ ] Endpoint classification: central-only endpoints (sign up, create band, my bands) return 404 on band hosts and band endpoints return 404 on the central host

### Acceptance criteria

- Requests are routed to the correct band only through the host, and spoofed forwarded headers have no effect

### Tests required

- Integration: `Host: a.localhost` resolves band A; `Host: unknown.localhost` → 404; `X-Forwarded-Host` from an untrusted source is ignored; uppercase, port and trailing dot are normalized; a central-only endpoint on a band host → 404

---

## M2-04 · Enforce row-level security per transaction

**Labels:** type:security, area:database, module:tenancy, priority:critical
**Depends on:** M1-04, M2-03

### Context

RLS is the last line of defense: even if a query forgets to filter by band, PostgreSQL returns nothing from other bands. The tenant setting must be transaction-local, so a pooled connection never carries a previous band.

### Scope

- [ ] At the start of every transaction, set `app.band_id` and `app.user_id` with `set_config(name, value, true)`, using only the validated `TenantContext` and the authenticated principal
- [ ] Implement the hook in one place (for example a transaction manager or connection preparer) and document the rule: tenant data is only accessed inside transactions
- [ ] Policy template for tenant tables: `ENABLE` and `FORCE ROW LEVEL SECURITY`; policy `USING (band_id = NULLIF(current_setting('app.band_id', true), '')::uuid)` with the same `WITH CHECK`, so an unset context returns no rows and rejects writes
- [ ] Convention: parent tables have `UNIQUE (band_id, id)`; child tables use composite foreign keys `(band_id, parent_id) REFERENCES parent (band_id, id)`; indexes start with `band_id`
- [ ] `memberships` policy allows `band_id = app.band_id OR user_id = app.user_id` (needed for "my bands")
- [ ] Jobs that process several bands run one transaction per band with that band's context; no bypass role exists
- [ ] Repositories also filter by band id (defense in depth)
- [ ] Migration snippet for new tenant tables in `docs/`

### Acceptance criteria

- With no context, tenant tables return zero rows and reject inserts
- The context is cleared at the end of each transaction

### Tests required

- Integration, connected as `bandhub_app`:
  - no context → zero rows; context A → only A's rows
  - insert with band B's id while the context is A → rejected by `WITH CHECK`; update moving a row to another band → rejected
  - pool leak test with pool size 1: a transaction for band A followed by one for band B sees no rows from A; a query outside a transaction afterwards sees nothing
  - a child row of band A pointing to a parent of band B → foreign key violation

---

## M2-05 · Add database schema guard tests for RLS

**Labels:** type:test, area:database, module:tenancy, priority:critical
**Depends on:** M2-04

### Context

The most likely future leak is a new table created without RLS. A test that inspects the schema after migrations turns that mistake into a failing build.

### Scope

- [ ] Integration test querying `pg_catalog` after migrations:
  - every table with a `band_id` column has `relrowsecurity` and `relforcerowsecurity` enabled and policies covering all commands
  - every foreign key between tenant tables includes `band_id`
  - every tenant table has an index (or primary key) whose first column is `band_id`
- [ ] Allowlist of global tables (for example `users`, `bands`, `refresh_tokens`, `flyway_schema_history`), each with a justification; a table without `band_id` that is not allowlisted fails the test
- [ ] `bandhub_app` owns nothing, is not superuser and has `rolbypassrls = false`

### Acceptance criteria

- A migration that adds a tenant table without RLS breaks the build (shown in the pull request with a temporary failing migration)

### Tests required

- The guard test itself, plus a negative fixture proving it detects a missing policy

---

## M2-06 · Implement user signup with email verification

**Labels:** type:feature, area:backend, module:identity, priority:critical
**Depends on:** M2-04

### Context

Accounts are global (one email, one account) and can belong to several bands. Verified email is required before creating a band or accepting an invitation.

### Scope

- [ ] `users` table (global): `id`, normalized lowercase `email` (unique), `password_hash`, `email_verified_at`, `display_name`, `status`, timestamps; accessed only by the `identity` module (ArchUnit rule)
- [ ] Argon2id password hashing (Spring Security `Argon2PasswordEncoder`, parameters documented) wrapped in a `DelegatingPasswordEncoder` for future upgrades
- [ ] Password policy: 12 to 128 characters, no composition rules, reject passwords from a bundled common-password list
- [ ] Verification token: 256-bit random, stored as a SHA-256 hash, single use, expires in 24 hours; resend endpoint
- [ ] Identical response whether the email exists or not; an existing account receives a "someone tried to sign up with your email" message instead
- [ ] Email port with an SMTP adapter, Mailpit in development, pt-BR templates; links always point to the central host
- [ ] Unverified accounts cannot log in to band hosts, create bands or accept invitations

### Acceptance criteria

- The full signup and verification flow works end to end through the API, with emails delivered to Mailpit locally

### Tests required

- Integration: signup → email received (GreenMail or a Mailpit container) → verify → `email_verified_at` set; reused and expired tokens rejected; duplicate email returns the same response and creates no second account; weak and common passwords rejected; `A@X.com` and `a@x.com` are the same account
- Unit: password policy

### Security notes

- Never log tokens, passwords or full email addresses

---

## M2-07 · Implement login with short-lived JWT and rotating refresh tokens

**Labels:** type:security, area:backend, module:identity, priority:critical
**Depends on:** M2-03, M2-06

### Context

Sessions are scoped to one host: a session on band A's subdomain is useless on band B's. The access token lives only in memory; the refresh token is an `HttpOnly` cookie that JavaScript cannot read.

### Scope

- [ ] `POST /api/auth/login` on a band host: email and password; requires a verified email and an active membership in that band. Wrong password, unknown email and missing membership return the same generic error; the password encoder runs even when the user does not exist
- [ ] The same endpoint on the central host issues a central session without `band_id`, limited to "my bands" and band creation
- [ ] Access token: JWT signed with HS256 (`JWT_SECRET`, at least 256 bits, `kid` header for rotation), 15-minute lifetime, claims `sub`, `band_id` (absent on central sessions), `role`, `aud` (the host), `iss`, `jti`; returned in the response body
- [ ] Refresh token: 256-bit random, stored hashed with family id, user, band, sliding expiry of 14 days and absolute expiry of 30 days; sent as cookie `__Host-bandhub_rt` (`Secure`, `HttpOnly`, `SameSite=Strict`, `Path=/`, no `Domain`)
- [ ] `POST /api/auth/refresh` rotates the refresh token; reuse of an already rotated token revokes the whole family; requires a custom header (for example `X-Requested-With`) as CSRF protection
- [ ] `POST /api/auth/logout` revokes the current family; `POST /api/auth/logout-all` revokes every session of the user
- [ ] Spring Security configured stateless with method security enabled

### Acceptance criteria

- Login, refresh, logout and logout-all work on band hosts and on the central host with the cookie flags above

### Tests required

- Integration:
  - successful login returns an access token and a `Set-Cookie` with every expected flag
  - a member of band A logging in on band B's host → generic 401
  - unverified account → 401
  - refresh rotates the token; reusing the old one revokes the family and both tokens stop working
  - expired refresh token → 401; refresh without the CSRF header → 403
  - logout and logout-all invalidate the right sessions
  - unknown email still runs the password encoder (verified with a spy)
- Unit: token claims and lifetimes with a fixed `Clock`

### Security notes

- Browsers treat `*.localhost` as a secure context, so `Secure` cookies work in local development over HTTP; document this in the README

---

## M2-08 · Implement self-service band creation

**Labels:** type:feature, area:backend, module:tenancy, priority:high
**Depends on:** M2-07

### Context

Any verified user can create a band on the central host and becomes its first admin and first member.

### Scope

- [ ] `POST /api/bands` on the central host (central session, verified email): `name` and `slug`
- [ ] Slug availability endpoint (authenticated and rate limited)
- [ ] One transaction creates the band, an `ADMIN` membership and a member linked to the user, then publishes a `BandCreated` event (used later, for example, by default categories in M5-02)
- [ ] Configurable limit of bands created per user (for example 5) to curb abuse
- [ ] Response includes the band URL for the redirect to the band host

### Acceptance criteria

- A new band is ready to log in to immediately after creation

### Tests required

- Integration: band, membership and member are created atomically (a failure in any step rolls back everything); taken slug → 409; reserved slug → 422; unverified user → 403; request on a band host → 404; two concurrent requests for the same slug → exactly one succeeds; per-user limit enforced

---

## M2-09 · Validate tenant and membership on every authenticated request

**Labels:** type:security, area:backend, module:tenancy, priority:critical
**Depends on:** M2-04, M2-07

### Context

A valid token is not enough: it must belong to the band of the current host, and the membership must still be active. Removing a member or changing a role must take effect immediately, not when the token expires.

### Scope

- [ ] After JWT validation, require `band_id` to equal the band resolved from the host and `aud` to match the host; central tokens are rejected on band hosts and band tokens on the central host
- [ ] Load the membership (role, status) on each request through a short cache (at most 60 seconds) that is evicted on membership changes; the role from the database is authoritative, the JWT claim is only a hint
- [ ] Set the RLS context (M2-04) only from the validated principal and tenant
- [ ] Invalid token → 401; resources of another band → 404, never revealing that they exist

### Acceptance criteria

- No authenticated request reaches a controller with a tenant that differs from the token's band

### Tests required

- Integration: band A's token on band B's host → 401; wrong signature, `alg: none` and a modified `band_id` claim → 401; expired token → 401; membership removed → the next request fails even with an unexpired token; role downgraded → permissions drop on the next request; central token on a band host → 401

---

## M2-10 · Define roles and permission matrix

**Labels:** type:security, area:backend, module:tenancy, priority:critical
**Depends on:** M2-09

### Context

Roles are per band: the same person can be admin in one band and read-only in another. The matrix is the single source of truth for both code and tests.

### Scope

- [ ] Roles `ADMIN`, `MEMBER` and `READ_ONLY` on the membership
- [ ] Validate the [proposed matrix](../roadmap.md#permission-matrix-proposal) with the band and publish it at `docs/security/permissions.md`
- [ ] Named permissions (for example `expense:write`, `merch:manage`) mapped from roles in one place; `@PreAuthorize("hasAuthority('…')")` on use cases; ownership rules ("own only") enforced in the service layer
- [ ] Test DSL: a matrix definition (Java table or YAML) that generates role × endpoint integration test cases; an endpoint without an entry fails the build (works together with the enumeration in M2-11)

### Acceptance criteria

- The matrix test covers every existing endpoint; `READ_ONLY` cannot perform any write

### Tests required

- Parametrized integration tests generated from the matrix

---

## M2-11 · Build cross-tenant isolation test harness

**Labels:** type:test, area:backend, module:tenancy, priority:critical
**Depends on:** M2-09, M2-10

### Context

Isolation must be proven for every endpoint, including the ones that will be written months from now. The harness makes this automatic: registering an endpoint in it is part of the Definition of Done, and forgetting to do so fails the build.

### Scope

- [ ] Fixture with two bands (A and B), users with every role, and one user who belongs to both bands
- [ ] Endpoint registry built from Spring's handler mappings: each endpoint must be annotated `@PublicEndpoint` (with a reason), `@CentralEndpoint`, or have isolation scenarios registered; otherwise the test fails
- [ ] Scenarios for each band endpoint:
  1. band A's token on band B's host
  2. band A's token with band B's resource IDs in path, query or body
  3. the user who belongs to both bands, using band A's token on band A's host, targeting band B's resources
  4. tampered `band_id` claim
  5. missing token
  6. expired token
- [ ] Assertions: expected 401/404, and the response body contains none of band B's identifiers or names
- [ ] After write attempts, verify band B's data is unchanged (read as the owner role)
- [ ] Guide in `docs/` on how to register a new endpoint

### Acceptance criteria

- The harness runs in CI on every pull request
- Adding a deliberately unprotected endpoint makes the build fail (shown in the pull request)

### Tests required

- The harness itself, plus the negative check above

---

## M2-12 · Build authentication pages and session handling in the frontend

**Labels:** type:feature, area:frontend, module:identity, priority:high
**Depends on:** M1-06, M2-07, M2-08

### Context

The SPA must keep the access token out of persistent storage and refresh it transparently, so a stolen localStorage or an XSS on one page cannot hand over a long-lived session.

### Scope

- [ ] Central host pages: sign up, email verification landing, login, create band (name and slug with live availability and a preview of `slug.bandhub.app`)
- [ ] Band host pages: login (showing the band name) and logout
- [ ] Access token kept only in memory, never in localStorage or sessionStorage; on load, call refresh; a single shared refresh in flight when several requests get 401, then retry; logout clears memory and the query cache
- [ ] Route guards and redirect back after login
- [ ] zod schemas that mirror backend rules; Problem Details mapped to field errors
- [ ] pt-BR copy; accessible forms; usable at 360 px

### Acceptance criteria

- A user can complete sign up → verify → create band → log in on the band host using only the UI

### Tests required

- Vitest + MSW: parallel 401 responses trigger exactly one refresh; the token is never written to storage (spy on `localStorage` and `sessionStorage`); form validation; Problem Details mapping
- End-to-end coverage arrives in M3-05
