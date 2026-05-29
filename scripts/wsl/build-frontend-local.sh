#!/usr/bin/env bash
# NebulaOps v24.1 local frontend builder.
# Uses one root frontend dependency install and a selective cache to avoid rebuilding unchanged MFEs.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if ! command -v npm >/dev/null 2>&1; then
  echo "[ERROR] npm is required on the host/WSL to build frontend dist folders."
  exit 1
fi
if ! command -v node >/dev/null 2>&1; then
  echo "[ERROR] node is required on the host/WSL to build frontend dist folders."
  exit 1
fi

export npm_config_cache="${NEBULAOPS_NPM_CACHE:-$ROOT_DIR/.build/npm-cache}"
mkdir -p "$npm_config_cache" "$ROOT_DIR/.build"

cd "$ROOT_DIR/frontend"

if [ "${NEBULAOPS_FORCE_FRONTEND_DIST_BUILD:-false}" = "true" ]; then
  echo "[NebulaOps] Force rebuild requested; rebuilding shell and every remote from the single frontend root install."
  node tools/build-changed-remotes.mjs --all
else
  echo "[NebulaOps] Running selective v24.1 frontend build from the single frontend root install."
  node tools/build-changed-remotes.mjs
fi

echo
echo "[NebulaOps] Local frontend dist build completed."
echo "You can now run:"
echo "  docker compose build frontend"
echo "  ./scripts/wsl/build-frontend-images.sh"
