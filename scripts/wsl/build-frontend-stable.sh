#!/usr/bin/env bash
# NebulaOps v22.5 stable frontend build.
# npm runs on host/WSL only; Docker only packages prebuilt dist into nginx.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

./scripts/wsl/build-frontend-local.sh
./scripts/wsl/build-frontend-images.sh
