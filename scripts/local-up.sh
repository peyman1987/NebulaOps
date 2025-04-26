#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
PROJECT_NAME="nebulaops-v21-1"
export COMPOSE_PARALLEL_LIMIT=${COMPOSE_PARALLEL_LIMIT:-2}
cd "$ROOT_DIR"
./scripts/wsl/prepare-kubeconfig-for-docker.sh
./scripts/wsl/prepare-runtime-tools.sh
docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" up --build
