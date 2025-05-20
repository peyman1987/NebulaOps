#!/usr/bin/env bash
# v21.2 — Stop NebulaOps stack.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

log_step "Stopping NebulaOps v21.2"
dc down "$@"
log_ok "All services stopped"
