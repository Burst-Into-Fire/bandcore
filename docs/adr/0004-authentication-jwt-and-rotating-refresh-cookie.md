# ADR-0004: Authentication with Spring Security, short-lived JWT and rotating refresh cookie

- **Status:** Accepted
- **Date:** 2026-10-29
- **Related:** RNF02, RNF04, RNF05, [M2-07](../roadmap/M02-multitenancy-auth-core.md), [M3-02](../roadmap/M03-security-hardening-delivery.md)

## Context

The API needs authentication that is cheap to run, portable to a VPS and safe against token theft. The web app is a PWA served from the same origin as the API (ADR-0005).

## Decision

- Authentication is implemented with Spring Security in the API. No external identity provider.
- Passwords are hashed with **Argon2id**.
- The **access token** is a JWT valid for a few minutes, scoped to one band (`band_id` claim), kept in memory by the web app (never in `localStorage`).
- The **refresh token** is opaque, stored hashed in the database, sent as an `HttpOnly`, `Secure`, `SameSite=Lax`, host-only cookie restricted to the refresh path, and **rotated on every use**. Reuse of an old refresh token revokes the whole token family.
- CSRF protection is enabled for the cookie-based endpoints when they are added. Until M2, every non-public route answers 401 (see `SecurityConfiguration`).
- Login is rate limited (M3-02).

## Alternatives considered

- **Server-side sessions.** Simple and revocable, but sticky state on the API and harder to scope per band subdomain.
- **External provider (Auth0, Cognito, Keycloak).** Less code, but per-user cost, vendor lock-in or another service to operate.
- **Long-lived JWT in localStorage.** Readable by any XSS; cannot be revoked.

## Consequences

- Access tokens cannot be revoked before they expire, so they must stay short-lived.
- Refresh token storage, rotation and reuse detection need careful integration tests.
- OAuth 2.0 social login can be added later on top of the same token model.
