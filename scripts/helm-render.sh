#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
helm template nebulaops "$ROOT_DIR/infrastructure/helm/nebulaops" --namespace nebulaops > "$ROOT_DIR/infrastructure/helm/rendered-nebulaops.yaml"
echo "Rendered Helm manifests: infrastructure/helm/rendered-nebulaops.yaml"
