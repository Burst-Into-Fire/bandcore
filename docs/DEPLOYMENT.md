# Deployment

The plan is a managed platform (Railway or Render, still to be decided) with automatic deploys from GitHub. The setup is kept portable so it can move to a VPS later.

## CI/CD

A push to `main` runs the tests, builds the images and publishes. Workflows live in `.github/workflows/` and filter by folder:

- Changes in `api/**` run the backend tests and deploy the API.
- Changes in `web/**` run the web tests and deploy the web app.
- Changes only in `docs/**` or the README trigger neither.

The repository is private, so the hosting platform needs permission to read it from your GitHub account or organization.

## Managed platform

1. Create the project on the chosen platform and connect the GitHub repository.
2. Create a managed PostgreSQL instance.
3. Create two services, one for `api/` and one for `web/`, each built from its own Dockerfile.
4. Set the environment variables listed in [Getting Started](GETTING-STARTED.md#environment-variables) in the platform settings. Use production values, never the local ones.
5. Enable automatic deploys from `main`.
6. Check the regions available. Prefer one close to Brazil to reduce latency.
7. Set up the backup routine below.

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