#!/usr/bin/env bash
# v21.4 — Stop NebulaOps stack.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

log_step "Stopping NebulaOps v21.4"
dc down "$@"

# Also stop legacy project names if still running
for LEGACY in nebulaops-v21-2-1 nebulaops-v21-3 nebulaops-v21-2; do
  if docker compose -p "$LEGACY" ps -q 2>/dev/null | grep -q .; then
    log_warn "Found running containers under legacy project '$LEGACY' — stopping them too"
    docker compose -p "$LEGACY" -f "$COMPOSE_FILE" down 2>/dev/null || \
      docker rm -f $(docker ps -aq --filter "label=com.docker.compose.project=$LEGACY") 2>/dev/null || true
  fi
done

log_ok "All services stopped"

