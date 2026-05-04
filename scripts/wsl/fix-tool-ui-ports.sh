#!/usr/bin/env bash
# v23.3 — Free RabbitMQ/Mongo/Redis UI ports when switching between native and SSO modes.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

for PORT in 15672 15673 8088 18088 8089 18089 ${NEBULAOPS_HTTP_PORT:-80} 4200 4211 4212 4213 4214 4215 4216 4217 4218 4219; do
  rows="$(docker ps --filter "publish=$PORT" --format '{{.ID}}|{{.Names}}|{{.Label "com.docker.compose.project"}}|{{.Label "com.docker.compose.service"}}' 2>/dev/null || true)"
  if [ -z "$rows" ]; then
    log_ok "Port $PORT is free"
    continue
  fi
  while IFS='|' read -r id name project service; do
    [ -z "$id" ] && continue
    case "$project" in
      nebulaops|nebulaops-*|nebulaops_v*|nebulaops-v*)
        log_warn "Removing stale NebulaOps container using port $PORT: $name ($project/$service)"
        docker rm -f "$id" >/dev/null 2>&1 || true
        ;;
      *)
        log_err "Port $PORT is used by non-NebulaOps container '$name'. Stop it manually if this is intentional."
        docker ps --filter "publish=$PORT" --format 'table {{.ID}}\t{{.Names}}\t{{.Ports}}'
        exit 1
        ;;
    esac
  done <<< "$rows"
 done
log_ok "Tool UI and micro frontend ports are ready"
