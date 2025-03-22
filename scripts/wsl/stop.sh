#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
PROJECT_NAME="nebulaops-v20-6"
docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" down --remove-orphans
echo "Stopped NebulaOps v20.6. Data volumes preserved."
