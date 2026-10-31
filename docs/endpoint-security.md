# Securing a new endpoint

Every endpoint goes through three automated guards. Forgetting one fails the build, on purpose: tenant isolation must hold for endpoints written months from now, not only for today's.

| Guard | Test | Fails when |
|---|---|---|
| Host scope and public flag | `TenantResolutionIT` | An endpoint answers on the wrong kind of host |
| Authorization matrix | `AuthorizationMatrixIT` | An authenticated endpoint has no entry in `AuthorizationMatrix`, or a role can do more (or less) than the entry says |
| Cross-tenant isolation | `TenantIsolationIT` | A band endpoint has no probe in `IsolationScenarios`, or any attack reaches band B |

The threat model behind them is in [security/threat-model.md](security/threat-model.md).

## 1. Choose the host scope

Endpoints live on band hosts (`{slug}.bandcore.app`) unless annotated otherwise. On the wrong host they answer 404, before authentication.

| Annotation | Host | Use for |
|---|---|---|
| none | Band hosts | Everything that reads or writes band data |
| `@CentralEndpoint` | `app.{BASE_DOMAIN}` | Sign up, band creation, "my bands" |
| `@AnyHostEndpoint` | Both | Login, refresh, logout, current user |
| `@PlatformEndpoint` | Any host, also unknown ones; no tenant | Status probes only |

## 2. Decide whether it needs authentication

Endpoints require a valid access token by default. Add `@PublicEndpoint("why")` only when the endpoint must work without one, and say why: the reason is reviewed like code. Public endpoints never read the `Authorization` header.

Unsafe requests (`POST`, `PUT`, `PATCH`, `DELETE`) without a bearer token must send `X-Requested-With` (CSRF protection for the cookie-based endpoints). The web app always sends it.

## 3. Check a permission in the use case

```java
@PreAuthorize(Permissions.CAN_MANAGE_BAND)
@Transactional
public Band update(String name, String timezone) { ... }
```

- Put the check on the service method, so every caller is covered.
- Take the band from `TenantContextHolder.require().bandId()`, never from the request.
- Repositories of tenant tables extend `TenantRepository` and name the band in every query (`findByBandIdAndId`). A resource of another band answers 404 (`ApiException.notFound()`), exactly like one that does not exist.
- New permission? Add it to `Permissions`, map it in `Role`, and update [security/permissions.md](security/permissions.md).

## 4. Add it to the authorization matrix

In `api/src/test/java/.../security/AuthorizationMatrix.java`:

```java
rule(HttpMethod.POST, "/expenses", EnumSet.of(ADMIN, MEMBER), fixture -> "{\"amountCents\": 1000}"),
```

The path is the template exactly as mapped, relative to `/api`. Path variables are filled from the fixture in `AuthorizationMatrixIT`. Central endpoints go in `CENTRAL`.

## 5. Register isolation probes

In `api/src/test/java/.../security/IsolationScenarios.java`:

```java
probe(HttpMethod.GET, "/expenses/{expenseId}"),
probe(HttpMethod.PATCH, "/expenses/{expenseId}", target -> "{\"description\": \"x\"}"),
```

When the path has a new kind of id, create that resource in band B in `TenantIsolationIT.World` and add it to `bravoTarget()` (and `alphaTarget()`). Add any new identifiers or names of band B to `bravoSecrets()`.

Each probe then runs six attacks against band B ("bravo-secret") from band A ("alpha"):

1. Band A's token on band B's host → 401
2. Band A's token on band A's host with band B's ids in the path, query and body → 404 for resource paths; otherwise band A's own data only
3. The same, from a user who belongs to both bands → same as 2
4. Band A's token with `band_id` and `aud` changed to band B → 401
5. No token on band B's host → 401
6. An expired token for band B → 401

For every attack, the response must contain none of band B's ids or names, and band B's rows in every tenant table must be unchanged afterwards.

## 6. Regenerate the web client

Run `./mvnw verify` in `api/`, then `npm run api:generate` in `web/`, and commit `web/src/api/schema.ts`. CI fails when the committed client is out of date.
