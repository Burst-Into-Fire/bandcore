#!/usr/bin/env bash
# The integration tests must use the same PostgreSQL image as docker-compose (and production).
set -euo pipefail
cd "$(dirname "$0")/.."

pom=$(sed -n 's:.*<postgres.image>\(.*\)</postgres.image>.*:\1:p' api/pom.xml)
compose=$(sed -n 's/^ *image: *\(postgres:[^ ]*\).*/\1/p' docker-compose.yml)

if [[ -z "$pom" || -z "$compose" || "$pom" != "$compose" ]]; then
  echo "PostgreSQL image mismatch: api/pom.xml has '${pom}', docker-compose.yml has '${compose}'." >&2
  exit 1
fi
echo "PostgreSQL image: ${pom}"
