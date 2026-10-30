#!/usr/bin/env bash
# Starts the whole stack with docker compose and checks it through the web proxy, like a browser.
# Runs as a separate compose project on separate ports, so it never touches your local stack.
#
#   scripts/compose-smoke-test.sh            # build, check, tear down
#   KEEP_STACK=1 scripts/compose-smoke-test.sh   # leave it running afterwards
set -euo pipefail

cd "$(dirname "$0")/.."

export COMPOSE_PROJECT_NAME=bandcore-smoke
export WEB_PORT=${SMOKE_WEB_PORT:-3100}
export DB_PORT=${SMOKE_DB_PORT:-5532}
export MAILPIT_UI_PORT=${SMOKE_MAILPIT_PORT:-8125}
WEB="http://127.0.0.1:${WEB_PORT}"
MAILPIT="http://127.0.0.1:${MAILPIT_UI_PORT}"

# Use .env.example values: the smoke test must work on a fresh clone.
compose() { docker compose --env-file .env.example "$@"; }

cleanup() {
  if [ "${KEEP_STACK:-0}" != "1" ]; then
    compose down --volumes --remove-orphans > /dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

fail() {
  echo "FAIL: $1" >&2
  compose ps >&2 || true
  compose logs --tail=80 api web >&2 || true
  exit 1
}

pass() { echo "ok   $1"; }

echo "Starting stack (project ${COMPOSE_PROJECT_NAME}, web on ${WEB})..."
compose up --detach --build --wait --wait-timeout 300 || fail "services did not become healthy"

body=$(curl -fsS -H 'Host: app.localhost' "${WEB}/api/status") || fail "GET /api/status through the proxy"
[[ "$body" == *'"status":"UP"'* ]] || fail "unexpected /api/status body: ${body}"
pass "API reachable through the proxy on the central host"

body=$(curl -fsS -H 'Host: burst.localhost' "${WEB}/api/status") || fail "GET /api/status on a band host"
pass "API reachable through the proxy on a band host"

headers=$(curl -fsS -D - -o /dev/null -H 'Host: burst.localhost' "${WEB}/expenses/123") || fail "SPA route"
[[ "${headers,,}" == *"cache-control: no-cache"* ]] || fail "index.html must not be cached"
[[ "${headers,,}" != *"server: nginx/"* ]] || fail "nginx must not reveal its version"
pass "SPA served with history fallback, no cache on index.html, no server version"

status=$(curl -s -o /dev/null -w '%{http_code}' -H 'Host: app.localhost' "${WEB}/api/actuator/health")
[[ "$status" != "200" ]] || fail "actuator must not be reachable through the proxy"
body=$(curl -s -H 'Host: app.localhost' "${WEB}/actuator/health")
[[ "$body" != *'"status"'* ]] || fail "management endpoints must not be proxied"
pass "management port not reachable through the proxy"

body=$(compose exec -T api wget -qO- http://127.0.0.1:8081/actuator/health/readiness) || fail "API readiness"
[[ "$body" == *'"UP"'* ]] || fail "API not ready: ${body}"
pass "API readiness UP on the management port"

# Sign up -> verify -> create band -> log in on the band host, through the proxy like a browser.
JSON=(-H 'Content-Type: application/json' -H 'X-Requested-With: XMLHttpRequest')
STAMP=$(date +%s)
EMAIL="smoke-${STAMP}@example.com"
PASSWORD='a long smoke test passphrase'
SLUG="smoke-${STAMP}"
access_token() { sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'; }

status=$(curl -s -o /dev/null -w '%{http_code}' -H 'Host: app.localhost' "${JSON[@]}" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"displayName\":\"Smoke\"}" "${WEB}/api/auth/signup")
[[ "$status" == "202" ]] || fail "sign up answered ${status}"
token=""
for _ in $(seq 1 50); do
  token=$(curl -fsS "${MAILPIT}/view/latest.txt" 2>/dev/null | grep -o '#token=[A-Za-z0-9_-]*' | head -1 | cut -d= -f2 || true)
  [[ -n "$token" ]] && break
  sleep 0.2
done
[[ -n "$token" ]] || fail "verification email did not reach Mailpit"
status=$(curl -s -o /dev/null -w '%{http_code}' -H 'Host: app.localhost' "${JSON[@]}" \
  -d "{\"token\":\"${token}\"}" "${WEB}/api/auth/verify-email")
[[ "$status" == "204" ]] || fail "verify email answered ${status}"
pass "sign up and email verification (link delivered to Mailpit)"

credentials="{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}"
central=$(curl -fsS -H 'Host: app.localhost' "${JSON[@]}" -d "$credentials" "${WEB}/api/auth/login" | access_token)
[[ -n "$central" ]] || fail "central login"
status=$(curl -s -o /dev/null -w '%{http_code}' -H 'Host: app.localhost' "${JSON[@]}" -H "Authorization: Bearer ${central}" \
  -d "{\"name\":\"Smoke Band\",\"slug\":\"${SLUG}\"}" "${WEB}/api/bands")
[[ "$status" == "201" ]] || fail "band creation answered ${status}"
pass "central login and band creation"

headers=$(curl -fsS -D - -o /tmp/bandcore-smoke-login.json -H "Host: ${SLUG}.localhost" "${JSON[@]}" -d "$credentials" "${WEB}/api/auth/login") \
  || fail "login on the band host"
cookie=$(grep -i '^set-cookie: __Host-bandcore_rt=' <<<"$headers" || true)
for flag in 'HttpOnly' 'Secure' 'SameSite=Strict' 'Path=/'; do
  [[ "$cookie" == *"$flag"* ]] || fail "refresh cookie without ${flag}: ${cookie}"
done
[[ "${cookie,,}" != *"domain="* ]] || fail "refresh cookie must be host-only"
band=$(access_token < /tmp/bandcore-smoke-login.json)
rm -f /tmp/bandcore-smoke-login.json
body=$(curl -fsS -H "Host: ${SLUG}.localhost" -H "Authorization: Bearer ${band}" "${WEB}/api/me") || fail "GET /api/me on the band host"
[[ "$body" == *'"role":"ADMIN"'* ]] || fail "creator is not the band admin: ${body}"
status=$(curl -s -o /dev/null -w '%{http_code}' -H "Host: ${SLUG}.localhost" -H "Authorization: Bearer ${central}" "${WEB}/api/me")
[[ "$status" == "401" ]] || fail "central token accepted on the band host (${status})"
status=$(curl -s -o /dev/null -w '%{http_code}' -H "Host: unknown-${STAMP}.localhost" "${WEB}/api/band/public-profile")
[[ "$status" == "404" ]] || fail "unknown band host answered ${status}"
pass "band login with a host-only refresh cookie; tokens stay on their host"

for service in api web; do
  uid=$(compose exec -T "$service" id -u) || fail "id -u in ${service}"
  [[ "$uid" != "0" ]] || fail "${service} must not run as root"
done
pass "api and web run as non-root users"

echo "Smoke test passed."
