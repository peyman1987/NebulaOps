#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
COMPOSE_FILE="$ROOT_DIR/infrastructure/docker-compose.yml"
PROJECT_NAME="nebulaops"
service="${1:-}"
if [[ -n "$service" ]]; then docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" logs -f --tail=200 "$service"; else docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" logs -f --tail=120; fi
