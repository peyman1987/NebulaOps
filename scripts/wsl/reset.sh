#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
COMPOSE_FILE="$ROOT_DIR/infrastructure/docker-compose.yml"
PROJECT_NAME="nebulaops"
echo "This removes containers and local volumes."
read -r -p "Continue? [y/N] " answer
case "$answer" in y|Y|yes|YES) docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" down -v --remove-orphans; echo "Reset complete.";; *) echo "Cancelled.";; esac
