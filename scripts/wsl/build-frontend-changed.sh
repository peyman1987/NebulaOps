#!/usr/bin/env bash
# NebulaOps v24.1 selective frontend build entry point.
# Keeps shell/remotes on one dependency cache and rebuilds only changed inputs.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export NEBULAOPS_FORCE_FRONTEND_DIST_BUILD="${NEBULAOPS_FORCE_FRONTEND_DIST_BUILD:-false}"
exec "$ROOT_DIR/scripts/wsl/build-frontend-local.sh"
