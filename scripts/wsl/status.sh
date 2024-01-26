#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
COMPOSE_FILE="$ROOT_DIR/infrastructure/docker-compose.yml"
PROJECT_NAME="nebulaops"
docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" ps
echo
for url in http://localhost:8080/actuator/health http://localhost:8081/api/auth/healthz http://localhost:8082/actuator/health http://localhost:8083/actuator/health http://localhost:8084/actuator/health; do
  printf "%-48s" "$url"; curl -fsS "$url" >/dev/null 2>&1 && echo OK || echo not-ready
done
