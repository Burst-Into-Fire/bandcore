# Getting Started

How to run **bandcore** on your machine.

## Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| Git | any recent | everything |
| Docker with Compose v2 | Docker Engine 24+ or Docker Desktop | running the stack and the API integration tests |
| JDK | 21 (Temurin recommended) | running the API or its tests outside Docker |
| Node.js | 24 LTS (see `web/.nvmrc`) | running the web app or its tests outside Docker |

Maven does not need to be installed: the API ships with the Maven wrapper (`mvnw` and `mvnw.cmd`).

After cloning, enable the Git hooks once (they validate branch names and commit messages, see [CONTRIBUTING](CONTRIBUTING.md)):

```bash
git config core.hooksPath .githooks
```

## Run with Docker

1. Clone the repository:

   ```bash
   git clone https://github.com/Burst-Into-Fire/bandcore.git
   cd bandcore
   ```

2. Create your `.env` from the example. The example values work as they are for local development.

   | Shell | Command |
   |---|---|
   | bash, Git Bash | `cp .env.example .env` |
   | Windows cmd | `copy .env.example .env` |
   | PowerShell | `Copy-Item .env.example .env` |

3. Start everything:

   ```bash
   docker compose up --build
   ```

| Service | URL |
|---|---|
| Web, central host | <http://app.localhost:3000> |
| Web, a band (any slug) | <http://burst.localhost:3000> |
| API, through the web proxy | <http://app.localhost:3000/api/status> |
| OpenAPI document | <http://app.localhost:3000/api/v3/api-docs> |
| Swagger UI (not in production) | <http://app.localhost:3000/api/swagger-ui.html> |
| Mailpit (emails sent locally) | <http://localhost:8025> |
| PostgreSQL | `localhost:5432`, database `bandcore` (development only) |

The browser always talks to the web container, which serves the app and forwards `/api` to the API, the same way it works in production. The API is not published on its own port, and its health endpoints (management port 8081) are only reachable inside the Docker network.

On the first start the database container creates the `bandcore_owner` and `bandcore_app` roles (see [Database](DATABASE.md)), and Flyway runs the migrations when the API starts. To stop:

```bash
docker compose down
```

Add `-v` to also delete the database volume and start from scratch (needed after changing the database passwords in `.env`).

### `*.localhost` subdomains

Each band has its own subdomain. Locally that is `{slug}.localhost`, which Chrome, Edge and Firefox resolve to `127.0.0.1` without any setup.

- **WSL 2:** run Docker inside WSL or Docker Desktop, and open the URLs in the Windows browser. Ports published on `127.0.0.1` in WSL are forwarded to Windows (default `localhostForwarding=true`).
- **Command line tools** do not always resolve `*.localhost`. Use the `Host` header instead: `curl -H 'Host: burst.localhost' http://127.0.0.1:3000/api/status`.

## Environment variables

Set in `.env` for docker-compose. In staging and production they are set in the hosting platform, never in a file.

| Variable | Used by | Description | Local default |
|---|---|---|---|
| `BASE_DOMAIN` | API | Base domain for band subdomains (`{slug}.BASE_DOMAIN`) | `localhost` |
| `WEB_PORT` | compose | Port of the web container on your machine | `3000` |
| `POSTGRES_DB` | db, API | Database name | `bandcore` |
| `POSTGRES_SUPERUSER_PASSWORD` | db | Password of the local `postgres` superuser, used only to bootstrap the roles | dev value |
| `DB_OWNER_PASSWORD` | db, API | Password of `bandcore_owner` (Flyway migrations) | dev value |
| `DB_APP_PASSWORD` | db, API | Password of `bandcore_app` (runtime connection) | dev value |
| `DB_PORT` | compose | PostgreSQL port on your machine | `5432` |
| `JWT_SECRET` | API (from M2) | Token signing secret, at least 32 random characters | empty |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` | API (from M2) | Outgoing email | Mailpit |
| `MAILPIT_UI_PORT` | compose | Mailpit inbox port | `8025` |

The API reads these variables (docker-compose sets them from the ones above):

| Variable | Description | Default outside the `prod` profile |
|---|---|---|
| `DB_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/bandcore` |
| `DB_APP_USER`, `DB_APP_PASSWORD` | Runtime database role | `bandcore_app`, `dev_only_app_password` |
| `DB_OWNER_USER`, `DB_OWNER_PASSWORD` | Migration role | `bandcore_owner`, `dev_only_owner_password` |
| `PORT` | API port | `8080` |
| `MANAGEMENT_PORT` | Health endpoints port (never exposed publicly) | `8081` |
| `SPRING_PROFILES_ACTIVE` | `prod` in staging and production | none |

With `SPRING_PROFILES_ACTIVE=prod` there are no defaults: the API refuses to start and lists every missing variable. Never commit `.env`; only `.env.example` goes to the repository.

## Run without Docker

Start only the database from the compose file:

```bash
docker compose up db
```

**API** (from the `api` folder). The defaults already point to the database above:

| Shell | Command |
|---|---|
| bash, Git Bash | `./mvnw spring-boot:run` |
| Windows cmd | `mvnw.cmd spring-boot:run` |

The API listens on <http://localhost:8080/api> and its health endpoints on <http://localhost:8081/actuator/health>.

**Web** (from the `web` folder):

```bash
npm install
npm run dev
```

Open <http://app.localhost:5173>. The Vite dev server forwards `/api` to `http://localhost:8080` (change it with `VITE_API_PROXY_TARGET`).

## Troubleshooting

- **Port already in use:** change `WEB_PORT`, `DB_PORT` or `MAILPIT_UI_PORT` in `.env`, or stop the other process.
- **`password authentication failed` after changing `.env`:** the roles are created only on the first start of the database. Run `docker compose down -v` to recreate them.
- **Migration error after changing a migration file:** applied migrations must never be edited. Create a new one, or reset the local database with `docker compose down -v`.
- **The API does not start with the `prod` profile:** the error lists the missing variables.
- **`*.localhost` does not open:** use Chrome, Edge or Firefox, or the `Host` header with `curl` (see above).
