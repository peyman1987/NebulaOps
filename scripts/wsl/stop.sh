#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
COMPOSE_FILE="$ROOT_DIR/infrastructure/docker-compose.yml"
PROJECT_NAME="nebulaops"
docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" down --remove-orphans
echo "Stopped. Data volumes preserved."
