# Deployment

The plan is a managed platform (Railway or Render, still to be decided) with automatic deploys from GitHub. The setup is kept portable so it can move to a VPS later.

## CI/CD

Workflows live in `.github/workflows/`:

| Workflow | When | What |
|---|---|---|
| `ci.yml` | Pull requests, pushes to `main` and `development` | Backend tests, frontend checks, API client drift check, Docker images with the compose smoke test and a Trivy scan |
| `security.yml` | Pull requests, pushes, weekly | CodeQL, gitleaks, dependency review |
| `validate-standards.yml` | Pull requests | Branch name, pull request title and commit messages |

Every job runs on every pull request (no path filters), so required checks never stay pending. Images are only built, not pushed: continuous deploy to staging comes in [M3-06](roadmap/M03-security-hardening-delivery.md), and the hosting platform is chosen in [M1-10](roadmap/M01-project-foundation.md).

Production containers run with `SPRING_PROFILES_ACTIVE=prod`. The roles and schema are created once with the bootstrap script described in [Database](DATABASE.md).

## Managed platform

1. Create the project on the chosen platform and connect the GitHub repository.
2. Create a managed PostgreSQL instance.
3. Run the role bootstrap script on the new database (see [Database](DATABASE.md#bootstrap)).
4. Create two services, one for `api/` and one for `web/`, each built from its own Dockerfile. Only the web service is public; it reaches the API over the private network (`API_UPSTREAM`).
5. Set the environment variables listed in [Getting Started](GETTING-STARTED.md#environment-variables) in the platform settings. Use production values, never the local ones.
6. Enable automatic deploys from `main`.
7. Check the regions available. Prefer one close to Brazil to reduce latency.
8. Set up the backup routine below.

## Backups

Platform backups are not enough on their own. Keep a copy outside the platform:

1. Schedule a daily `pg_dump` (a GitHub Actions scheduled workflow works well).
2. Send the dump to object storage on another provider.
3. Keep a retention policy, for example 7 daily and 4 weekly copies.
4. Test a restore at least once before going live, and again every few months.

## Costs

Define a monthly ceiling (RNF34) and review the platform's usage page regularly. Prices and plan limits change, so confirm them before committing.

## Moving to a VPS

1. Provision a Linux server, a domain and an SSH key. Open only ports 22, 80 and 443.
2. Install Docker with Compose v2.
3. Restore the latest database backup on the new host.
4. Copy the environment variables to a `.env` file on the server.
5. Use a production compose file with the database, the API, the web app and a reverse proxy such as Caddy for automatic HTTPS.
6. Start with `docker compose -f docker-compose.prod.yml up -d`.
7. Point DNS to the new host and keep the old one running until the new one is confirmed.