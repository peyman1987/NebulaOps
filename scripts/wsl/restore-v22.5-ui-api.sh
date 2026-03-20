#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
echo "▶ Restoring v23.1 dedicated MFE UI/API bundles"
./scripts/wsl/ensure-frontend-dist.sh
docker compose -p nebulaops-v23-1 -f docker-compose.yml build \
  frontend \
  mfe-docker-desktop \
  mfe-openlens-kubernetes \
  mfe-task-management \
  mfe-infra-hub
docker compose -p nebulaops-v23-1 -f docker-compose.yml up -d \
  frontend \
  mfe-docker-desktop \
  mfe-openlens-kubernetes \
  mfe-task-management \
  mfe-infra-hub
echo "✓ UI/API restore applied"
echo "Open: http://nebulaops.localhost/?v=v23.1.9-restore-ui-live-api"
echo "Then use Ctrl+Shift+R or clear site data for localhost if old JS is still cached."
