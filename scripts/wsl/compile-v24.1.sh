#!/usr/bin/env bash
# NebulaOps v24.1 full compile entry point.
# Runs frontend build, remote syntax/contract verification, backend JAR build and
# static preflight in one repeatable command for WSL/Docker Desktop.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/scripts/wsl/lib/common.sh"
cd "$ROOT_DIR"

FRONTEND_ONLY=false
BACKEND_ONLY=false
CLEAN=false
FORCE=false
for arg in "$@"; do
  case "$arg" in
    --frontend-only) FRONTEND_ONLY=true ;;
    --backend-only) BACKEND_ONLY=true ;;
    --clean) CLEAN=true ;;
    --force) FORCE=true ;;
    -h|--help)
      cat <<USAGE
Usage: ./scripts/wsl/compile-v24.1.sh [--clean] [--force] [--frontend-only|--backend-only]

Runs the v24.1 compilation pipeline:
  1. compile readiness guard
  2. frontend shell + changed remotes build
  3. remoteEntry contract verification
  4. backend Maven build with host Maven or Dockerized Maven fallback
  5. integrated v24.1 preflight

Use --frontend-only when validating UI changes without Maven/Docker.
Use --backend-only when validating Java changes after the frontend already passed.
USAGE
      exit 0
      ;;
  esac
done

if [ "$FRONTEND_ONLY" = "true" ] && [ "$BACKEND_ONLY" = "true" ]; then
  log_err "Choose either --frontend-only or --backend-only, not both"
  exit 1
fi

"$ROOT_DIR/scripts/wsl/verify-compile-readiness-v24.1.sh"

if [ "$BACKEND_ONLY" != "true" ]; then
  log_step "Compiling frontend shell and changed MFE remotes"
  if [ "$FORCE" = "true" ]; then
    NEBULAOPS_FORCE_FRONTEND_DIST_BUILD=true "$ROOT_DIR/scripts/wsl/build-frontend-changed.sh"
  else
    "$ROOT_DIR/scripts/wsl/build-frontend-changed.sh"
  fi
  log_step "Verifying remote bundles"
  node frontend/tools/verify-remotes.mjs
  node frontend/tools/verify-frontend-architecture-v24.1.mjs
fi

if [ "$FRONTEND_ONLY" != "true" ]; then
  log_step "Compiling backend JARs"
  backend_args=()
  [ "$CLEAN" = "true" ] && backend_args+=(--clean)
  [ "$FORCE" = "true" ] && backend_args+=(--force)
  "$ROOT_DIR/scripts/wsl/build-backend-jars.sh" "${backend_args[@]}"
fi

log_step "Running final v24.1 preflight"
"$ROOT_DIR/scripts/wsl/preflight-v24.1.sh"
log_ok "NebulaOps v24.1 compile pipeline completed"
