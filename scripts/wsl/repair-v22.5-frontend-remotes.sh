#!/usr/bin/env bash
# Compatibility wrapper. The maintained repair script for this package is v23.1.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
exec "$ROOT_DIR/scripts/wsl/repair-v23.1-frontend-remotes.sh" "$@"
