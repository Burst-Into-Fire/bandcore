# M9 · Offline Sales (PWA)

**Goal:** Let members sell at venues with bad signal: an installable PWA, an offline catalog, a local sales queue that syncs later, an idempotent sync endpoint, and the reconciliation workflow for sales that exceed stock.

**Exit criteria:** with the phone offline, a member can sell from the cached catalog; on reconnect the sales sync without duplicates; sales that exceed stock become pending and an admin resolves them, with stock never negative.

**Depends on milestones:** M8

---

## M9-01 · Make the app an installable PWA with app shell caching

**Labels:** type:feature, area:frontend, priority:high
**Depends on:** M8-06

### Context

The app must open without a connection. Only the app shell and static assets are cached by the service worker; offline business data goes through explicit IndexedDB stores (M9-02) where it can be controlled and wiped.

### Scope

- [ ] vite-plugin-pwa (Workbox): web manifest, icons, precached app shell and static assets, navigation fallback
- [ ] Update flow with a *Nova versão disponível* prompt; never reload silently during a sale
- [ ] No runtime caching of API responses in the service worker
- [ ] Installation guidance for iOS Safari

### Acceptance criteria

- After the first visit, the app opens offline and can be installed on Android and iOS

### Tests required

- Playwright: the app loads offline after a first visit; the update prompt appears when a new version is deployed

---

## M9-02 · Store offline catalog snapshot in IndexedDB

**Labels:** type:feature, area:frontend, module:merch, priority:high
**Depends on:** M9-01

### Context

Selling offline needs the catalog, prices and last known stock on the device. Phones at the merch table are often shared, so this data must be minimal and removable.

### Scope

- [ ] Dexie database (per origin, so per band): catalog (active products, variants, prices), stock snapshot, open events, session metadata (user id)
- [ ] Refresh when online: on app start, on an interval and after each sync
- [ ] Staleness indicator (*Catálogo atualizado há 2 h*)
- [ ] Wipe the catalog stores on logout, user change or loss of membership (401 on refresh)
- [ ] No tokens and no finance data in IndexedDB
- [ ] Versioned Dexie schema with migrations

### Acceptance criteria

- With the network off, the quick sale screen renders from the snapshot

### Tests required

- Vitest with fake-indexeddb: snapshot replacement; wipe on logout and on 401
- E2E: go offline → the catalog still renders

### Security notes

- Only data needed to sell is stored offline; balances and expenses never are

---

## M9-03 · Implement idempotent batch sync endpoint

**Labels:** type:feature, area:backend, module:merch, priority:critical
**Depends on:** M8-02

### Context

Phones resend when a response is lost, and two phones can sell the last unit while offline. The server must process each sale exactly once and never let stock go negative.

### Scope

- [ ] `POST /api/sales/sync` with up to 100 sales, processed in `sold_at` order, each in its own transaction (one failure does not roll back the others)
- [ ] Result per sale:
  - `ACCEPTED`: stock decremented and sale completed
  - `DUPLICATE`: the id was already processed; returns the original result
  - `PENDING_RECONCILIATION`: not enough stock, or the event closed in the meantime; the sale is stored without moving stock
  - `REJECTED`: invalid (unknown or archived variant, payload different from an existing id, user no longer a member)
- [ ] `sold_at` kept, validated (not in the future beyond a tolerance, not older than a configured number of days); `received_at` set by the server; `source = OFFLINE`
- [ ] The seller is always the authenticated user, never taken from the payload
- [ ] Rate limit and request size limit

### Acceptance criteria

- Resending any batch has no effect beyond the first time, and concurrent syncs never make stock negative

### Tests required

- Integration: a batch with mixed outcomes returns the right statuses; resending the same batch → all `DUPLICATE` with the original outcomes and no extra stock movement; two users syncing the last unit concurrently → one `ACCEPTED`, one `PENDING_RECONCILIATION`; a sale referencing another band's variant → `REJECTED` with no data touched; closed event → pending; authorization matrix and isolation entries; audit events

---

## M9-04 · Queue sales offline and sync on reconnect

**Labels:** type:feature, area:frontend, module:merch, priority:critical
**Depends on:** M9-02, M9-03

### Context

When there is no connection, a sale is stored locally and sent later. A sale in the queue is real money already received, so it must never be lost silently.

### Scope

- [ ] When offline (or when the request fails because of the network), store the sale in an `outbox` store with its client UUID, `sold_at` (device clock plus a monotonic sequence), seller user id and band
- [ ] Optimistic local stock decrement in the snapshot
- [ ] Pending badge with the count of queued sales
- [ ] Sync triggers: `online` event, app start, manual *Sincronizar agora*, and Background Sync where supported (not on iOS)
- [ ] Refresh the session before syncing; if it expired, ask the user to log in again and keep the queue
- [ ] The queue belongs to the user who created it; another user on the same device cannot see or sync it
- [ ] Logout is blocked while the queue has sales, until they sync; discarding them requires an explicit two-step in-page confirmation
- [ ] Handle each result from M9-03: `ACCEPTED` and `DUPLICATE` are removed; `PENDING_RECONCILIATION` is removed with a notice; `REJECTED` moves to a "failed" list with the reason

### Acceptance criteria

- No queued sale is lost through reconnects, retries or session expiry

### Tests required

- Vitest: outbox state machine; retries with backoff; the same UUID is kept across retries; logout blocked with pending sales
- E2E in M9-06

---

## M9-05 · Implement pending reconciliation workflow

**Labels:** type:feature, area:backend, area:frontend, module:merch, priority:high
**Depends on:** M9-03, M7-05

### Context

A pending sale means the physical stock and the system disagreed. An admin decides what actually happened.

### Scope

- [ ] List of pending sales, with a badge for admins
- [ ] Confirm: the admin enters the adjustment (for example, units that were miscounted); one transaction creates an `ADJUSTMENT` (reason `RECONCILIATION`) and the `SALE` movement and completes the sale
- [ ] Cancel: the sale becomes `CANCELLED` with no stock movement, with a note about returning the money
- [ ] Pending sales shown in the event report; closing an event is blocked while it has pending sales
- [ ] Everything audited

### Acceptance criteria

- Every pending sale is resolved by an admin, and stock stays non-negative and consistent with the ledger

### Tests required

- Integration: confirming keeps stock non-negative and the ledger consistent; cancelling creates no movement; two concurrent resolutions → one wins; `MEMBER` → 403; closing an event with pending sales → 409; isolation entry
- Vitest: resolution screens

---

## M9-06 · Add Playwright E2E tests for offline sale journeys

**Labels:** type:test, area:frontend, priority:high
**Depends on:** M9-04, M9-05

### Context

Offline behavior is the hardest to get right and the easiest to break. These journeys run in CI with the network switched off in the browser.

### Scope

- [ ] Journeys using `context.setOffline(true)`:
  1. sell three items offline → reconnect → synced and stock correct
  2. two browser contexts (two devices) offline sell the last unit → reconnect → one completed, one pending → an admin confirms with an adjustment
  3. the network drops during the sync response → resend → no duplicates
  4. logout is blocked while sales are pending
  5. the offline flow at a 360 px viewport

### Acceptance criteria

- All journeys pass in CI and are required checks

### Tests required

- The journeys above
