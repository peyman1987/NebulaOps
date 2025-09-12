#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
PROJECT_NAME="nebulaops-v21-4"
export COMPOSE_PARALLEL_LIMIT=${COMPOSE_PARALLEL_LIMIT:-2}
cd "$ROOT_DIR"

# Ensure shared network exists (external: true in docker-compose requires it)
if ! docker network inspect nebulaops-network &>/dev/null; then
  echo "[v21.4] Creating shared network: nebulaops-network"
  docker network create nebulaops-network
else
  echo "[v21.4] Network nebulaops-network already exists — skipping"
fi

./scripts/wsl/prepare-kubeconfig-for-docker.sh 2>/dev/null || true
./scripts/wsl/prepare-runtime-tools.sh 2>/dev/null || true
docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" up --build "$@"
