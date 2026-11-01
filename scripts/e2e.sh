#!/usr/bin/env bash
# Runs the Playwright journeys in e2e/ against a fresh docker compose stack, as a separate compose
# project on separate ports (never touches your local stack). Playwright runs in its official
# image, so no browser or Node install is needed on the host.
#
#   scripts/e2e.sh                     # build, run the journeys, tear down
#   KEEP_STACK=1 scripts/e2e.sh        # leave the stack running afterwards
#   scripts/e2e.sh -g isolation        # extra arguments go to `playwright test`
set -euo pipefail

cd "$(dirname "$0")/.."

export COMPOSE_PROJECT_NAME=bandcore-e2e
export WEB_PORT=${E2E_WEB_PORT:-3200}
export DB_PORT=${E2E_DB_PORT:-5632}
export MAILPIT_UI_PORT=${E2E_MAILPIT_PORT:-8225}

# Keep in sync with @playwright/test in e2e/package.json.
PLAYWRIGHT_IMAGE=mcr.microsoft.com/playwright:v1.64.1-noble

compose() { docker compose --env-file .env.example "$@"; }

cleanup() {
  if [ "${KEEP_STACK:-0}" != "1" ]; then
    compose down --volumes --remove-orphans > /dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

echo "Starting stack (project ${COMPOSE_PROJECT_NAME}, web on http://app.localhost:${WEB_PORT})..."
compose up --detach --build --wait --wait-timeout 300

status=0
docker run --rm --network host --ipc=host \
  -u "$(id -u):$(id -g)" -e HOME=/tmp -e CI \
  -e E2E_WEB_PORT="${WEB_PORT}" -e E2E_MAILPIT_URL="http://127.0.0.1:${MAILPIT_UI_PORT}" \
  -v "$PWD/e2e:/e2e" -w /e2e "$PLAYWRIGHT_IMAGE" \
  sh -c 'npm ci --no-audit --no-fund --loglevel=error && npx playwright test "$@"' playwright "$@" || status=$?

if [ "$status" != "0" ]; then
  echo "E2E failed; traces and videos are in e2e/test-results (open with: npx playwright show-trace)." >&2
  compose logs --tail=80 api web >&2 || true
fi
exit "$status"
