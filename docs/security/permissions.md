# Roles and permissions

Roles are per band: the same person can be `ADMIN` in one band and `READ_ONLY` in another ([ADR-0003](../adr/0003-global-accounts-per-band-memberships.md)). The backend enforces this matrix; the web app only hides what a user cannot do.

- **Last reviewed:** 2026-10-30 (M2-10). The proposal from the [roadmap](../roadmap.md#permission-matrix-proposal) is adopted as is; it still needs to be validated with the band before M4, when members can be invited with each role.
- **Source of truth in code:** `Role` (role → permissions) and `Permissions` (names). Use cases check a permission with `@PreAuthorize(Permissions.CAN_...)`.
- **Proof:** `AuthorizationMatrixIT` generates one test per role and endpoint from `AuthorizationMatrix`, and fails when an authenticated endpoint is missing from it. `RoleTest` checks that `READ_ONLY` only has `:read` permissions.

## Capabilities

| Capability | ADMIN | MEMBER | READ_ONLY | Permission |
|---|:---:|:---:|:---:|---|
| View band, dashboard and members | Yes | Yes | Yes | `band:read`, `member:read` |
| View expenses, balances and settlements | Yes | Yes | Yes | `finance:read` |
| View merch, events and reports | Yes | Yes | Yes | `merch:read` |
| Create expenses and settlements | Yes | Yes | No | `finance:write` |
| Edit or delete expenses and settlements | All | Own only | No | `finance:manage` (all), `finance:write` + ownership check (own) |
| Mark a planned expense as paid | Yes | Own only | No | `finance:manage` (all), `finance:write` + ownership check (own) |
| Create events and register sales | Yes | Yes | No | `sale:write` |
| Refund sales | Yes | Open events only | No | `sale:refund` + open-event check for members |
| Manage products, variants, batches and stock adjustments | Yes | No | No | `merch:manage` |
| Close events and resolve pending reconciliations | Yes | No | No | `event:close` |
| Manage members, invitations and roles | Yes | No | No | `member:manage` |
| Change band settings | Yes | No | No | `band:manage` |
| View the audit trail | Yes | No | No | `audit:read` |

"Own only" and "open events only" are not permissions: the service checks them after the permission, and answers 404 (not 403) when the record belongs to someone else and must stay hidden, or 403 when it is visible but not editable.

## Permissions by role

| Permission | ADMIN | MEMBER | READ_ONLY |
|---|:---:|:---:|:---:|
| `band:read` | ✓ | ✓ | ✓ |
| `member:read` | ✓ | ✓ | ✓ |
| `finance:read` | ✓ | ✓ | ✓ |
| `merch:read` | ✓ | ✓ | ✓ |
| `finance:write` | ✓ | ✓ | |
| `sale:write` | ✓ | ✓ | |
| `sale:refund` | ✓ | ✓ | |
| `band:manage` | ✓ | | |
| `member:manage` | ✓ | | |
| `finance:manage` | ✓ | | |
| `merch:manage` | ✓ | | |
| `event:close` | ✓ | | |
| `audit:read` | ✓ | | |

## Endpoints today

| Endpoint | Host | Who |
|---|---|---|
| `GET /api/me`, `POST /api/auth/logout-all` | any | Any authenticated user |
| `GET /api/band` | band | `band:read` |
| `PATCH /api/band` | band | `band:manage` |
| `GET /api/members`, `GET /api/members/{id}` | band | `member:read` |
| `GET /api/me/bands`, `GET /api/bands/slug-availability`, `POST /api/bands` | central | Any authenticated account (creating a band also needs a verified email) |
| `POST /api/auth/signup`, `/verify-email`, `/resend-verification` | central | Public |
| `POST /api/auth/login`, `/refresh`, `/logout` | any | Public (refresh and logout use the refresh cookie) |
| `GET /api/band/public-profile` | band | Public (band name for the login page) |
| `GET /api/status` | any | Public |

## Adding an endpoint

1. Put `@PreAuthorize(Permissions.CAN_...)` on the use case (service method), never only on the controller. Add a constant to `Permissions` and the mapping in `Role` if a new permission is needed, and update the tables above.
2. Add a rule to `AuthorizationMatrix` with the roles allowed.
3. Register the endpoint in the isolation harness ([guide](../endpoint-security.md)).
