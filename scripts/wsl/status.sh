#!/usr/bin/env bash
# v23.1 — WSL status helper. Use health.sh for endpoint checks.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

dc ps --format "table {{.Service}}\t{{.Status}}\t{{.Ports}}"
echo
echo "For Keycloak-aware endpoint checks run: ./scripts/wsl/health.sh"
