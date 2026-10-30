# ADR-0001: Modular monolith with Spring Modulith

- **Status:** Accepted
- **Date:** 2026-10-29
- **Related:** [M1-03](../roadmap/M01-project-foundation.md), [roadmap](../roadmap.md#reference-architecture)

## Context

bandcore starts with a small team and two business areas (finance and merch) that share bands, members and money. Future modules (press, Instagram, plans and billing) must be addable without tangling the code. Operating several services would cost more (hosting, deploys, distributed transactions) than the team can afford, and sales must update stock in the same database transaction.

## Decision

The API is a single Spring Boot application split into modules with [Spring Modulith](https://spring.io/projects/spring-modulith). Top-level packages under `com.burstintofire.bandcore` are modules: `identity`, `tenancy`, `finance`, `merch`, `audit` and the open module `shared`.

- A module's root package is its public API; subpackages are internal.
- `shared` holds cross-cutting infrastructure (errors, request id, clock, security baseline, later `Money`) and depends on no other module.
- `ApplicationModules.verify()` runs in the unit test suite (`ModularityTest`), so a forbidden dependency or a cycle fails the build.
- New business areas are new top-level modules.

## Alternatives considered

- **Microservices.** Independent deploys, but distributed transactions between sales and stock, more infrastructure and higher cost. Not justified at this size.
- **Layered monolith (controllers, services, repositories packages).** Simple at first, but nothing stops modules from reaching into each other; boundaries erode quickly.
- **Maven multi-module build.** Strong boundaries, but more build ceremony; Modulith gives the same verification with plain packages and can be split later.

## Consequences

- One deployable, one database, local transactions.
- Module boundaries are verified on every build and documented in `target/spring-modulith-docs`.
- Cross-module calls go through the target module's root package (or application events), never its internals.
- A module can later be extracted into a service if it ever needs to scale independently.
