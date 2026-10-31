#!/bin/sh
# Runs on the first start of the postgres container (docker-entrypoint-initdb.d).
# The entrypoint may source this file instead of executing it, so do not call exit or set -u.

: "${DB_OWNER_PASSWORD:?DB_OWNER_PASSWORD is required}"
: "${DB_APP_PASSWORD:?DB_APP_PASSWORD is required}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v db_name="$POSTGRES_DB" \
  -v owner_password="$DB_OWNER_PASSWORD" \
  -v app_password="$DB_APP_PASSWORD" \
  -f /docker-entrypoint-initdb.d/create-roles.psql
