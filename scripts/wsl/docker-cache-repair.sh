#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
COMPOSE_FILE="$ROOT_DIR/infrastructure/docker-compose.yml"
PROJECT_NAME="nebulaops"
cat <<'INFO'
NebulaOps Docker cache repair
This repairs common Docker Desktop/WSL BuildKit snapshot errors such as:
  parent snapshot ... does not exist: not found
  failed to prepare extraction snapshot

It removes build cache and stopped containers. Application data volumes are preserved unless you run reset.sh separately.
INFO

docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" down --remove-orphans || true

echo "Pruning BuildKit builder cache..."
docker builder prune -af || true

echo "Pruning unused images/layers..."
docker image prune -af || true

echo "Pruning unused buildx cache..."
docker buildx prune -af || true

echo "Repair completed. Rebuild with:"
echo "  DOCKER_BUILDKIT=1 docker compose -p $PROJECT_NAME -f infrastructure/docker-compose.yml build --no-cache frontend"
echo "  docker compose -p $PROJECT_NAME -f infrastructure/docker-compose.yml up -d"
