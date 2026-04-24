#!/usr/bin/env bash
# NebulaOps v23.2 — compatibility helper for k3s/containerd extension images.
# k3s/containerd is safer with a local registry than with ctr tar imports, so this
# delegates to the normal extension deployer in registry mode.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

EXT="${1:-apiforge}"
export NEBULAOPS_EXTENSIONS_IMAGE_MODE=registry
exec "$ROOT_DIR/scripts/wsl/deploy-extensions-k8s.sh" --only "$EXT"
