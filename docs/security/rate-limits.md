# Rate limits

Limits on the endpoints that attackers target for credential stuffing, enumeration and flooding ([M3-02](../roadmap/M03-security-hardening-delivery.md)). They are enforced in every environment, with the values in `api/src/main/resources/application.yml` (`bandcore.rate-limit.limits`).

## Limits

Each limit is a token bucket per key. A request takes one token. When the bucket is empty the API answers `429 Too Many Requests` with the `TOO_MANY_REQUESTS` code and a `Retry-After` header (seconds). The bucket refills completely at the end of its period.

| Endpoint | Limit name | Key | Limit |
|---|---|---|---|
| `POST /api/auth/login` (any host) | `login-ip` | client address | 20 per 5 minutes |
| | `login-failures-account` | email | 5 failures per 15 minutes |
| `POST /api/auth/signup` | `signup-ip` | client address | 10 per hour |
| | `signup-email` | email | 3 per hour |
| `POST /api/auth/verify-email` | `verify-email-ip` | client address | 30 per hour |
| `POST /api/auth/resend-verification` | `verification-resend-ip` | client address | 10 per hour |
| | `verification-resend-email` | email | 3 per hour |
| `POST /api/auth/password-reset` | `password-reset-ip` | client address | 10 per hour |
| | `password-reset-email` | email | 3 per hour |
| `POST /api/auth/password-reset/confirm` | `password-reset-confirm-ip` | client address | 20 per hour |
| `GET /api/bands/slug-availability` | `slug-availability-ip` | client address | 120 per minute |
| | `slug-availability-account` | user id | 60 per minute |
| `POST /api/invitations`, `POST /api/invitations/{id}/resend` | `invitations-band` | band | 30 per day |
| `POST /api/invitations/preview` | `invitation-preview-ip` | client address | 30 per hour |
| `POST /api/invitations/accept` | `invitation-accept-ip` | client address | 20 per hour |
| `POST /api/auth/handoff/exchange` | `handoff-exchange-ip` | client address | 60 per 10 minutes |

## How the limits behave

- **Login failures lock the account for a while, never for good.** Every login attempt takes a token from the account's bucket and a successful login gives it back, so only failures count. After 5 failures the account answers 429 for up to 15 minutes, even with the right password, on every host. The lock looks the same for an email that has no account, so it reveals nothing.
- **Per-account limits apply when the address changes.** An attacker rotating addresses still hits the email's bucket.
- **Emails are keys after normalization** (trimmed, lowercase), the same way accounts are stored.
- **Validation errors do not count.** Bean Validation rejects a malformed request before the limit is checked.

## Client address

The client address is the TCP remote address. When that address is a trusted proxy (`TRUSTED_PROXIES`: addresses or CIDR ranges, comma-separated), the client is the right-most `X-Forwarded-For` entry that is not a trusted proxy. Entries to the left of it were written by the client and are ignored, and a value that is not an IP address is never used. With `TRUSTED_PROXIES` empty, forwarded headers are ignored.

In production, set `TRUSTED_PROXIES` to the platform's proxy range, otherwise every client shares the proxy's address and its limits.

## Storage

Buckets live in the memory of the API instance (`InMemoryRateLimiter`), behind the `RateLimiter` interface, and are lost on restart. With more than one instance, each one would allow the full limit. Before scaling out, replace the implementation with one backed by a shared store (Bucket4j has PostgreSQL and Redis integrations). Idle buckets are dropped every 5 minutes.

## Adding a limit

1. Add the limit to `bandcore.rate-limit.limits` in `application.yml` and to the table above.
2. Resolve it in the constructor of the class that uses it (`limiter.limit("name")`), so a missing configuration stops the startup.
3. Call `consume(key)` before doing the work, and add an integration test that exceeds it (see `RateLimitIT`).
