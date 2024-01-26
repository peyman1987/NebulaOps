#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
echo "NebulaOps local verification"
missing=0
for c in docker curl; do
  if command -v "$c" >/dev/null 2>&1; then echo "OK $c"; else echo "MISSING $c"; missing=1; fi
done
for c in node npm jq helm kubectl; do
  if command -v "$c" >/dev/null 2>&1; then echo "OK $c"; else echo "OPTIONAL missing $c"; fi
done
docker compose version >/dev/null 2>&1 && echo "OK docker compose" || { echo "MISSING docker compose plugin"; missing=1; }
[ -f frontend/package.json ] && echo "OK Angular frontend present" || missing=1
[ -f infrastructure/docker-compose.yml ] && echo "OK Docker Compose present" || missing=1
[ -f infrastructure/helm/nebulaops/Chart.yaml ] && echo "OK Helm chart present" || missing=1
[ -f docs/architecture-animated.svg ] && echo "OK architecture SVG present" || missing=1
python3 scripts/validate-package.py
if [[ "$missing" == "1" ]]; then
  echo "Some required tools/files are missing. See messages above."
  exit 1
fi
echo "Local static verification completed. For WSL use: ./scripts/wsl/check-wsl.sh && ./scripts/wsl/start.sh"
