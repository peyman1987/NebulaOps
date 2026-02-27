#!/usr/bin/env bash
# v22.5 — Stop NebulaOps stack quickly.
# GitLab CE is intentionally treated as optional/heavy and is killed first by default.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

KEEP_GITLAB=false
STOP_TIMEOUT="${STOP_TIMEOUT:-20}"
for arg in "$@"; do
  case "$arg" in
    --keep-gitlab) KEEP_GITLAB=true ;;
    -h|--help)
      cat <<USAGE
Usage: $0 [--keep-gitlab]
  --keep-gitlab   Do not force-remove the optional GitLab CE container first.

Environment:
  STOP_TIMEOUT=20  Compose/Docker graceful shutdown timeout in seconds.
USAGE
      exit 0
      ;;
  esac
done

cd "$ROOT_DIR"

force_remove_service() {
  local svc="$1"
  local ids
  ids="$(docker ps -aq \
    --filter "label=com.docker.compose.project=$PROJECT_NAME" \
    --filter "label=com.docker.compose.service=$svc" 2>/dev/null || true)"
  if [ -n "$ids" ]; then
    log_warn "Force-removing optional heavy service: $svc"
    docker rm -f $ids >/dev/null 2>&1 || true
  fi
}

log_step "Stopping NebulaOps v22.5"

if [ -x "$ROOT_DIR/scripts/wsl/stop-extensions-port-forward.sh" ]; then
  "$ROOT_DIR/scripts/wsl/stop-extensions-port-forward.sh" >/dev/null 2>&1 || true
fi

if [ "$KEEP_GITLAB" != "true" ]; then
  force_remove_service gitlab
fi

# Prefer a bounded graceful shutdown. If it still hangs/fails, fall back to force removal by compose label.
if command -v timeout >/dev/null 2>&1; then
  timeout "$((STOP_TIMEOUT + 35))s" docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" down --remove-orphans --timeout "$STOP_TIMEOUT" || {
    log_warn "Compose down did not finish cleanly; forcing remaining NebulaOps containers"
    docker rm -f $(docker ps -aq --filter "label=com.docker.compose.project=$PROJECT_NAME") >/dev/null 2>&1 || true
  }
else
  dc down --remove-orphans --timeout "$STOP_TIMEOUT" || {
    log_warn "Compose down failed; forcing remaining NebulaOps containers"
    docker rm -f $(docker ps -aq --filter "label=com.docker.compose.project=$PROJECT_NAME") >/dev/null 2>&1 || true
  }
fi

# Also stop legacy project names if still running
for LEGACY in nebulaops-v22-5 nebulaops-v22-1 nebulaops-v21-4 nebulaops-v20-2 nebulaops-v19-5 nebulaops; do
  if docker compose -p "$LEGACY" ps -q 2>/dev/null | grep -q .; then
    log_warn "Found running containers under legacy project '$LEGACY' — stopping them too"
    docker compose -p "$LEGACY" -f "$COMPOSE_FILE" down --remove-orphans --timeout "$STOP_TIMEOUT" 2>/dev/null || \
      docker rm -f $(docker ps -aq --filter "label=com.docker.compose.project=$LEGACY") 2>/dev/null || true
  fi
done


# Clean up stale NebulaOps containers that may keep tool UI ports bound across version upgrades.
for PORT in 15672 15673 8088 18088 8089 18089 4200 4211 4212 4213 4214 4215 4216 4217 4218 4219; do
  docker ps --filter "publish=$PORT" \
    --format '{{.ID}}|{{.Names}}|{{.Label "com.docker.compose.project"}}|{{.Label "com.docker.compose.service"}}' 2>/dev/null \
    | while IFS='|' read -r id name project service; do
        [ -z "$id" ] && continue
        case "$project" in
          nebulaops|nebulaops-*|nebulaops_v*|nebulaops-v*)
            log_warn "Removing stale NebulaOps container still binding port $PORT: $name ($project/$service)"
            docker rm -f "$id" >/dev/null 2>&1 || true
            ;;
        esac
      done
 done

log_ok "All services stopped"
