#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

NAME="${1:-Dev Tenant}"

echo "Provisioning tenant '${NAME}' (no public HTTP endpoint; CLI only)..."
./mvnw -q -pl ingestion-api spring-boot:run \
  -Dspring-boot.run.arguments="provision-tenant --name=${NAME} --spring.main.web-application-type=none"
