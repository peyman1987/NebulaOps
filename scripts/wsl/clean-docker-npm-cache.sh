#!/usr/bin/env bash
# Clean Docker BuildKit/npm cache if npm fails with "Exit handler never called".
set -euo pipefail

echo "[NebulaOps] Cleaning Docker builder cache used by frontend npm installs"
docker builder prune -f --filter type=exec.cachemount || true

echo
echo "Now rebuild sequentially:"
echo "  ./scripts/wsl/build-frontend-stable.sh"
