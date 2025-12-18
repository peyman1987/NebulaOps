#!/usr/bin/env bash
# NebulaOps v22.3 one-command launcher.
# Builds frontend dist locally, packages frontend runtime images, builds backend/images,
# starts the stack with SSO proxy, then runs health and smoke checks.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

WITH_GITLAB=false
SKIP_BUILD=false
SKIP_FRONTEND=false
SKIP_SMOKE=false
NO_CACHE_FRONTEND=false
WITH_SSO_PROXY=true
COMPOSE_ARGS=()

usage() {
  cat <<'USAGE'
Usage:
  ./scripts/wsl/run-v22.3.sh [options]

Default behavior:
  1. build Angular shell and all MFE dist folders on WSL/host
  2. build frontend runtime nginx images
  3. docker compose build backend/remaining images
  4. start with ./scripts/wsl/start.sh --with-sso-proxy
  5. run health and v22.3 live smoke checks

Options:
  --with-gitlab          Also start optional GitLab service.
  --no-sso-proxy         Start without OAuth2 proxy wrappers.
  --skip-build           Skip all Docker build steps and only start.
  --skip-frontend        Skip frontend local/image build.
  --skip-smoke           Skip smoke tests after start.
  --no-cache-frontend    Clean Docker BuildKit npm/cache and force local npm reinstall where possible.
  -h, --help             Show this help.

Examples:
  ./scripts/wsl/run-v22.3.sh
  ./scripts/wsl/run-v22.3.sh --with-gitlab
  ./scripts/wsl/run-v22.3.sh --skip-build
USAGE
}

for arg in "$@"; do
  case "$arg" in
    --with-gitlab) WITH_GITLAB=true ;;
    --no-sso-proxy) WITH_SSO_PROXY=false ;;
    --skip-build) SKIP_BUILD=true ;;
    --skip-frontend) SKIP_FRONTEND=true ;;
    --skip-smoke) SKIP_SMOKE=true ;;
    --no-cache-frontend) NO_CACHE_FRONTEND=true ;;
    -h|--help) usage; exit 0 ;;
    *) echo "[ERROR] Unknown argument: $arg"; usage; exit 1 ;;
  esac
done

need_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] Required command not found: $1"
    exit 1
  fi
}

banner() {
  echo
  echo "══════════════════════════════════════════════════════════════════"
  echo "$1"
  echo "══════════════════════════════════════════════════════════════════"
}

banner "NebulaOps v22.3 one-command startup"
echo "Workspace: $ROOT_DIR"

need_cmd docker
need_cmd npm
need_cmd python3

if [ -f scripts/validate-package.py ]; then
  banner "1) Package validation"
  python3 scripts/validate-package.py
fi

if [ "$SKIP_BUILD" = false ]; then
  if [ "$SKIP_FRONTEND" = false ]; then
    if [ "$NO_CACHE_FRONTEND" = true ]; then
      banner "2) Cleaning frontend build cache"
      rm -rf frontend/node_modules frontend/.angular frontend/dist || true
      find frontend/remotes -maxdepth 2 -type d \( -name node_modules -o -name .angular -o -name dist \) -prune -exec rm -rf {} + || true
      ./scripts/wsl/clean-docker-npm-cache.sh || true
    fi

    banner "2) Building frontend dist locally"
    ./scripts/wsl/build-frontend-local.sh

    banner "3) Building frontend runtime images"
    ./scripts/wsl/build-frontend-images.sh
  else
    banner "2) Frontend build skipped"
  fi

  banner "4) Docker Compose build"
  docker compose build
else
  banner "2) Build skipped"
fi

banner "5) Starting NebulaOps"
START_ARGS=()
if [ "$WITH_SSO_PROXY" = true ]; then
  START_ARGS+=(--with-sso-proxy)
fi
if [ "$WITH_GITLAB" = true ]; then
  START_ARGS+=(--with-gitlab)
fi
./scripts/wsl/start.sh "${START_ARGS[@]}"

banner "6) Health check"
./scripts/wsl/health.sh

if [ "$SKIP_SMOKE" = false ]; then
  banner "7) Smoke test"
  if [ -x ./scripts/wsl/smoke-v22.3-live.sh ]; then
    ./scripts/wsl/smoke-v22.3-live.sh || {
      echo "[WARN] v22.3 live smoke reported failures. Check logs with ./scripts/wsl/logs.sh"
    }
  elif [ -x ./scripts/wsl/smoke-v22.3.sh ]; then
    ./scripts/wsl/smoke-v22.3.sh || {
      echo "[WARN] v22.3 smoke reported failures. Check logs with ./scripts/wsl/logs.sh"
    }
  fi
fi

banner "NebulaOps v22.3 is started"
cat <<'NEXT'
Useful URLs:
  Shell:        http://nebulaops.localhost
  Gateway:      http://localhost:8080/actuator/health
  Keycloak:     http://nebulaops.localhost/keycloak
  Grafana:      http://localhost:3000
  RabbitMQ:     http://localhost:15672
  Mongo Express:http://localhost:8088
  Redis UI:     http://localhost:8089

Useful commands:
  ./scripts/wsl/status.sh
  ./scripts/wsl/logs.sh
  ./scripts/wsl/health.sh
  ./scripts/wsl/stop.sh
NEXT
