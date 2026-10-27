# Getting Started

How to run **bandcore** on your machine.

## Prerequisites

- Git
- Docker with Compose v2 (Docker Desktop on Windows)
- JDK 21 (only to run the API outside Docker)
- Node.js 24 LTS or newer (only to run the web app outside Docker)

Maven does not need to be installed: the API ships with the Maven wrapper (`mvnw` and `mvnw.cmd`).

## Run with Docker

1. Clone the repository (it is private, so you need access):

   ```bash
   git clone https://github.com/Burst-Into-Fire/bandcore.git
   cd bandcore
   ```

2. Create your `.env` from the example:

   | Shell | Command |
   |---|---|
   | bash, Git Bash | `cp .env.example .env` |
   | Windows cmd | `copy .env.example .env` |
   | PowerShell | `Copy-Item .env.example .env` |

3. Fill in the values (see [Environment variables](#environment-variables)).

4. Start everything:

   ```bash
   docker compose up --build
   ```

| Service | Default URL |
|---|---|
| Web | `http://localhost:5173` |
| API | `http://localhost:8080` |
| API docs (OpenAPI) | `http://localhost:8080/swagger-ui.html` |
| PostgreSQL | `localhost:5432` (development only) |

These are defaults. The source of truth is `docker-compose.yml`.

Flyway migrations run automatically when the API starts. To stop:

```bash
docker compose down
```

Add `-v` to also delete the database volume and start from scratch.

## Environment variables

| Variable | Description | Example |
|---|---|---|
| `POSTGRES_DB` | Database name | `bandcore` |
| `POSTGRES_USER` | Database user | `bandcore` |
| `POSTGRES_PASSWORD` | Database password | a strong random value |
| `SPRING_DATASOURCE_URL` | JDBC connection URL | `jdbc:postgresql://db:5432/bandcore` |
| `JWT_SECRET` | Secret used to sign tokens (at least 32 random characters) | generate one, never reuse it |
| `API_PORT` | API port | `8080` |
| `VITE_API_URL` | API URL used by the web app | `http://localhost:8080` |

Never commit `.env`. Only `.env.example` goes to the repository, with placeholder values.

## Run without Docker

You still need a PostgreSQL instance. The easiest way is to start only the database from the compose file:

```bash
docker compose up db
```

**API** (from the `api` folder):

| Shell | Command |
|---|---|
| bash, Git Bash | `./mvnw spring-boot:run` |
| Windows cmd | `mvnw.cmd spring-boot:run` |

**Web** (from the `web` folder):

```bash
npm install
npm run dev
```

## Troubleshooting

- **Port already in use:** change the port in `.env` or stop the other process.
- **Migration error after changing a migration file:** applied migrations must never be edited. Create a new one, or reset the local database with `docker compose down -v`.
- **API cannot reach the database:** check that `SPRING_DATASOURCE_URL` uses the compose service name (`db`) when running in Docker, and `localhost` when running the API outside Docker.