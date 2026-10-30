# ADR-0010: Integration-first testing strategy

- **Status:** Accepted
- **Date:** 2026-10-29
- **Related:** RNF31, [M1-05](../roadmap/M01-project-foundation.md), [testing strategy](../roadmap.md#testing-strategy), [docs/TESTING.md](../TESTING.md)

## Context

The rules that matter most (tenant isolation, money, stock never negative) depend on the database: constraints, transactions, locks, RLS and grants. Mocks and in-memory databases do not reproduce them.

## Decision

- **Integration tests are the main layer** (`*IT`, run by Failsafe): the whole application on a random port, called over real HTTP, against PostgreSQL in Testcontainers using the same image as production. Flyway runs as the owner and the application connects as `bandcore_app`, so RLS and grants behave exactly as in production.
- **Unit and property-based tests** (`*Test`, Surefire, jqwik) cover pure logic: allocation, balances, settlement plans, average cost.
- **Architecture tests** (Spring Modulith `verify()`, ArchUnit) run with the unit tests.
- `./mvnw verify` runs everything; CI runs it on every pull request and blocks merges on failure.
- No H2 or other in-memory database.

## Alternatives considered

- **Unit tests with mocks first.** Fast, but they pass while the SQL, the constraints or the RLS policies are wrong.
- **In-memory database.** Different SQL dialect, no RLS, different locking.

## Consequences

- Docker is required to run the integration tests, locally and in CI.
- The suite is slower than pure unit tests; one shared container (reusable locally) keeps it fast. The baseline is about 50 seconds for `./mvnw verify` on a laptop.
- Test infrastructure lives in `api/src/test/java/.../support`: `IntegrationTest`, `ApiClient` (with per-band `Host`), `PostgresTestDatabase`, `DatabaseCleaner`, `TestClockConfiguration`.
