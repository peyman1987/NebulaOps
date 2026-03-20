#!/usr/bin/env bash
# NebulaOps v23.1 LIVE compile and runtime verification.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

echo "[NebulaOps] v23.1 LIVE verification"

require() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] Required command not found: $1"
    exit 1
  fi
}

require java
require npm
require docker

if ! command -v mvn >/dev/null 2>&1; then
  echo "[ERROR] Maven is required for backend compilation."
  echo "Install Maven in WSL: sudo apt-get update && sudo apt-get install -y maven"
  exit 1
fi

echo
echo "1) Static package validation"
python3 scripts/validate-package.py

echo
echo "2) Backend compile"
mvn -f backend/pom.xml -DskipTests clean package

echo
echo "3) Frontend and MFE local dist build"
./scripts/wsl/build-frontend-local.sh

echo
echo "4) Frontend runtime image build"
./scripts/wsl/build-frontend-images.sh

echo
echo "5) Docker Compose config"
docker compose -f docker-compose.yml config --quiet

echo
echo "6) Targeted Docker build for live components"
docker compose build \
  gateway-service \
  release-orchestrator-service \
  policy-governance-service \
  audit-service \
  notification-service \
  ai-ops-service \
  devsecops-service \
  cost-analytics-service \
  mfe-release-center \
  mfe-policy-center \
  mfe-notification-center \
  mfe-identity-admin \
  mfe-progressive-delivery \
  mfe-infra-hub

echo
echo "7) Runtime start + health"
./scripts/wsl/stop.sh || true
./scripts/wsl/start.sh --with-sso-proxy
./scripts/wsl/health.sh
./scripts/wsl/smoke-v23.1-live.sh

echo
echo "[NebulaOps] v23.1 LIVE verification completed successfully."
