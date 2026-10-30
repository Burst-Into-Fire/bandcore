# ADR-0005: Subdomain per band and same-origin API behind a reverse proxy

- **Status:** Proposed (not in the requirements docs, which take the band from the token and a band selector; to be confirmed with the hosting spike, [M1-10](../roadmap/M01-project-foundation.md))
- **Date:** 2026-10-29
- **Related:** RNF01, [M1-07, M1-10](../roadmap/M01-project-foundation.md), [M2-03, M2-09](../roadmap/M02-multitenancy-auth-core.md), [M4-05](../roadmap/M04-band-workspace.md)

## Context

The browser keeps cookies, `localStorage` and IndexedDB per origin. Offline sales (M9) store data in IndexedDB, and a mistake there could mix two bands' sales on the same phone. CORS configuration is a frequent source of security bugs.

## Decision

- Each band has a subdomain: `{slug}.{BASE_DOMAIN}`. `app.{BASE_DOMAIN}` is the central host (sign up, create band, "my bands").
- The web container (nginx) serves the SPA and proxies `/api` to the API on the private network, on every host. The API is always same-origin: no CORS.
- The API resolves the band from the `Host` header; forwarded headers are trusted only from the configured proxy. The JWT `band_id` must match the band of the host.
- Locally, `*.localhost` resolves to the loopback address (`http://burst.localhost:3000`).

## Alternatives considered

- **Path per band** (`/b/{slug}/...`). One origin for all bands, so storage and cookies are shared between bands.
- **Band from the token only, with a band selector** (requirements docs). Simpler hosting, but one origin for all bands, with the storage problem above.
- **Separate API domain with CORS.** More configuration, cross-site cookies, and a larger attack surface.

## Consequences

- Browser storage and cookies are isolated per band for free.
- Hosting must support wildcard custom domains with automatic TLS and private networking; the spike M1-10 validates this before M2.
- Switching bands is a navigation to another origin, so it needs a secure handoff (M4-05).
