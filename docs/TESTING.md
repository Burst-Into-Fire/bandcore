# Testing

Integration tests against a real PostgreSQL are the main layer ([ADR-0010](adr/0010-integration-first-testing.md)). The full strategy, including the security guards that come with M2, is in the [roadmap](roadmap.md#testing-strategy).

## Running the tests

**API** (from the `api` folder). Docker must be running for the integration tests.

| What | bash, Git Bash | Windows cmd |
|---|---|---|
| Unit and integration tests | `./mvnw verify` | `mvnw.cmd verify` |
| Unit tests only (no Docker) | `./mvnw test` | `mvnw.cmd test` |
| One integration test class | `./mvnw verify -Dit.test=DatabaseRolesIT` | `mvnw.cmd verify -Dit.test=DatabaseRolesIT` |

Reports after `./mvnw verify`:

| Report | Path |
|---|---|
| Coverage (unit and integration combined) | `api/target/site/jacoco/index.html` |
| Module diagrams (Spring Modulith) | `api/target/spring-modulith-docs/` |
| OpenAPI document (input of the web client) | `api/target/openapi/openapi.json` |

To keep the PostgreSQL container between local runs (much faster), add `testcontainers.reuse.enable=true` to `~/.testcontainers.properties`.

**Web** (from the `web` folder):

```bash
npm test              # Vitest, once
npm run test:watch    # Vitest in watch mode
npm run lint          # ESLint (includes accessibility and i18n rules)
npm run typecheck     # TypeScript
npm run format:check  # Prettier
```

**Whole stack** (from the root): `scripts/compose-smoke-test.sh` builds the images, starts docker-compose as a separate project and checks the app through the web proxy.

## Test layers

| Layer | Naming | Runner | What it covers |
|---|---|---|---|
| Integration | `*IT` | Failsafe | The application on a random port, called over HTTP, against PostgreSQL in Testcontainers |
| Unit and property-based | `*Test` | Surefire | Pure logic, with JUnit 5, AssertJ and jqwik (`@Property`) |
| Architecture | `*Test` | Surefire | Module boundaries (`ModularityTest`) and ArchUnit rules (`ArchitectureRulesTest`): no `float`/`double` in money packages, no field injection, controllers never use repositories, `bands` and `users` only reached through their modules, tenant repositories always name the band |
| Security guards | `*IT` | Failsafe | `SchemaGuardIT` (every tenant table has forced RLS, policies, a `band_id` index and composite keys), `AuthorizationMatrixIT` (role × endpoint), `TenantIsolationIT` (six cross-tenant attacks per band endpoint). See [Securing a new endpoint](endpoint-security.md) |
| Web | `*.test.ts(x)` | Vitest | Logic and components with React Testing Library; the API is mocked with MSW |

### Writing an integration test

Extend `IntegrationTest` (in `api/src/test/java/com/burstintofire/bandcore/support`):

```java
class ExpenseIT extends IntegrationTest {

	@Test
	void listsTheBandsExpenses() {
		ResponseEntity<String> response = api.forBand("burst").get("/api/expenses");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

}
```

What the base class gives you:

- **The production setup.** One PostgreSQL container for the whole run, same image as production, bootstrapped with the same role script. Flyway runs as `bandcore_owner`; the application connects as `bandcore_app`, so grants and row-level security are real.
- **`api`**, an HTTP client that never throws on 4xx or 5xx. `api.forBand("burst")` sends `Host: burst.localhost`, `api.central()` sends `Host: app.localhost`.
- **A fixed clock**: `TestClockConfiguration.NOW` (inject `java.time.Clock` in production code, never call `Instant.now()`).
- **A clean database** after each test (tables are truncated as the owner).
- `PostgresTestDatabase.ownerConnection()` and `appConnection()` for direct SQL checks.

- **`fixtures`** (`TestFixtures`): accounts, bands, memberships and members written straight to the database, and `fixtures.login(api.forBand("alpha"), email)` for an access token.
- **Emails**: an in-process SMTP server (`TestMailServer.awaitEmail(to)`), emptied after each test.
- `TestTokens` builds signed, expired, tampered and unsigned tokens; `Cookies` reads and sends the refresh cookie.

The fixed clock and the database disagree on "now": compare stored timestamps with `TestClockConfiguration.NOW`, not with SQL `now()`.

Row-level security is forced for the owner too. `OwnerSql.inBand(bandId, ...)` writes tenant rows with the band's context; `PostgresTestDatabase.superuserConnection()` reads every band's rows at once, for assertions only.

## What must be covered

The critical business rules come first (RNF31). Each item below must have tests before the feature is considered done.

- **Splitting:** equal split with remainders, custom split that does not add up (rejected), shares always summing to the total (RN01).
- **Balances:** correct balance per member after expenses and settlement payments, computed per currency (RN02, RN08).
- **Stock:** a sale deducts stock, a sale above the available stock is rejected, stock never goes negative (RN03).
- **Refunds:** stock returns and revenue is reverted (RN04).
- **Historical profit:** changing a product price does not change past sales (RN05).
- **Tenant isolation:** a user from band A cannot read or change data from band B (RNF01). Every band endpoint must be registered in the isolation harness ([guide](endpoint-security.md)).
- **Authorization:** each role can only do what it is allowed to (RN06, RNF03).
- **Currency:** amounts in BRL and USD are never mixed in totals or balances (RN08).
- **Idempotency:** sending the same offline sale twice creates only one sale (RNF13), once offline sync exists.

## In the pipeline

The `CI` workflow runs on every pull request and on pushes to `main` and `development`:

| Job | What it runs |
|---|---|
| `backend` | `./mvnw verify` (unit and integration tests); uploads coverage and the OpenAPI document |
| `frontend` | `npm ci`, format check, lint, typecheck, tests, build |
| `api-contract` | Regenerates the typed API client from the OpenAPI document and fails if the committed one differs |
| `docker` | Builds both images, runs the compose smoke test and scans the images with Trivy |

A failing job blocks the merge (see `ruleset.json`). The `Security` workflow adds CodeQL, gitleaks and dependency review.
