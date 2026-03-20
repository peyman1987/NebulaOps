#!/usr/bin/env bash
# Controlled full build for NebulaOps v23.1.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

CLEAN=false
for arg in "$@"; do
  case "$arg" in
    --clean) CLEAN=true ;;
    -h|--help)
      cat <<'USAGE'
Usage: ./scripts/wsl/build-v23.1.sh [--clean]

Runs the deterministic build path:
  1. validate frontend dist/remoteEntry artifacts
  2. build backend JARs once with shared Maven cache
  3. build Docker images without Maven downloads inside each backend image
USAGE
      exit 0
      ;;
  esac
done

log_step "Validating frontend dist and remote entries"
"$ROOT_DIR/scripts/wsl/ensure-frontend-dist.sh"

if [ "$CLEAN" = "true" ]; then
  "$ROOT_DIR/scripts/wsl/build-backend-jars.sh" --clean --force
else
  "$ROOT_DIR/scripts/wsl/build-backend-jars.sh" --force
fi

log_step "Building Docker images"
export COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-2}"
dc build
log_ok "Docker image build completed"
