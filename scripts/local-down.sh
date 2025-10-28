#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
PROJECT_NAME="nebulaops-v22-1"
cd "$ROOT_DIR"
docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" down -v --remove-orphans
