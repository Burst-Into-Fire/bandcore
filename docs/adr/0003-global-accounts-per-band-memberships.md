# ADR-0003: Global user accounts with per-band memberships; members without login

- **Status:** Accepted (2026-10-30, before M2: the data model supports several bands per user from the start; the UI for it arrives in M4-05)
- **Date:** 2026-10-29
- **Related:** RF01, RF04, RF05, RF06, [M2-06](../roadmap/M02-multitenancy-auth-core.md), [M4-01, M4-05](../roadmap/M04-band-workspace.md)

## Context

Musicians often play in more than one band. Some band members never use the app but still share costs and appear in balances. Roles (admin, member, read-only) are per band: someone can be admin of one band and read-only in another.

## Decision

- A **user** is a global account: one email, one account, one password, no `band_id`.
- A **membership** links a user to a band with a role and a status. Roles are always evaluated per band.
- A **member** (integrante) is a tenant entity used in expenses, splits and balances. It may be linked to a user (when that person has an account) or exist without login.
- Only the `identity` module reads the users table (verified by ArchUnit once the table exists).

## Alternatives considered

- **Accounts scoped to one band** (same email can register again per band). Simpler data model, but duplicated passwords and a poor experience for people in several bands; migrating later is painful.
- **Only users, no separate members.** Forces every musician to create an account just to share a cost.

## Consequences

- Login is independent from the band; the band comes from the host (see ADR-0005) and must match an active membership.
- Even if the UI shows one band per user at first, the data model already supports several.
- Deleting an account (LGPD) anonymizes the user while the member keeps the band's history consistent.
