# Testing

## Running the tests

**API** (from the `api` folder):

| Shell | Command |
|---|---|
| bash, Git Bash | `./mvnw test` |
| Windows cmd | `mvnw.cmd test` |

**Web** (from the `web` folder):

```bash
npm test
```

Integration tests for the API should use Testcontainers with a real PostgreSQL, so Docker needs to be running. Avoid an in-memory database: behavior such as constraints and transactions must match production.

## What must be covered

The critical business rules come first (RNF31). Each item below should have tests before the feature is considered done.

- **Splitting:** equal split with remainders, custom split that does not add up (rejected), shares always summing to the total (RN01).
- **Balances:** correct balance per member after expenses and settlement payments, computed per currency (RN02, RN08).
- **Stock:** a sale deducts stock, a sale above the available stock is rejected, stock never goes negative (RN03).
- **Refunds:** stock returns and revenue is reverted (RN04).
- **Historical profit:** changing a product price does not change past sales (RN05).
- **Tenant isolation:** a user from band A cannot read or change data from band B (RNF01).
- **Authorization:** each role can only do what it is allowed to (RN06, RNF03).
- **Currency:** amounts in BRL and USD are never mixed in totals or balances (RN08).
- **Idempotency:** sending the same offline sale twice creates only one sale (RNF13), once offline sync exists.

## In the pipeline

The CI workflow runs the API tests when `api/**` changes and the web tests when `web/**` changes. A failing test blocks the deploy.