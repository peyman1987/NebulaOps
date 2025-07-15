#!/usr/bin/env bash
# v21.3 — NebulaOps startup script.
# Usage: ./scripts/wsl/start.sh [--rebuild-gateway]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

REBUILD_GATEWAY=false
for arg in "$@"; do
  case "$arg" in
    --rebuild-gateway) REBUILD_GATEWAY=true ;;
    -h|--help)
      cat <<USAGE
Usage: $0 [--rebuild-gateway]
  --rebuild-gateway    Force no-cache rebuild of the gateway-service image
                       (use when gateway routes/config changed)
USAGE
      exit 0
      ;;
  esac
done

cd "$ROOT_DIR"

log_step "Pre-flight checks"
"$ROOT_DIR/scripts/wsl/check-wsl.sh"

log_step "Preparing kubeconfig and runtime tools"
"$ROOT_DIR/scripts/wsl/prepare-kubeconfig-for-docker.sh"
"$ROOT_DIR/scripts/wsl/prepare-runtime-tools.sh"

log_step "Validating Grafana provisioning"
defaults=$(grep -R "isDefault: true" -n \
  infrastructure/observability/grafana/provisioning/datasources 2>/dev/null | wc -l || true)
if [ "$defaults" -ne 1 ]; then
  log_err "Grafana must have exactly one isDefault datasource (found $defaults)"
  exit 1
fi
log_ok "Grafana datasource provisioning OK"

if [ "$REBUILD_GATEWAY" = "true" ]; then
  log_step "Force-rebuilding gateway-service (no cache)"
  dc build --no-cache gateway-service
fi

log_step "Starting NebulaOps v21.3"
export COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-2}"
dc up --build -d

log_step "Waiting for gateway-service"
wait_http "http://localhost:8080/api/health" 120 "gateway-service" || \
  log_warn "Inspect logs: ./scripts/wsl/logs.sh gateway-service"

cat <<INFO

${C_BOLD}NebulaOps v21.3 is running.${C_RESET}

  ${C_CYAN}Frontend${C_RESET}    http://localhost:4200
  ${C_CYAN}Gateway${C_RESET}     http://localhost:8080/actuator/health
  ${C_CYAN}Grafana${C_RESET}     http://localhost:3000        admin/admin
  ${C_CYAN}Prometheus${C_RESET}  http://localhost:9090
  ${C_CYAN}RabbitMQ${C_RESET}    http://localhost:15672       guest/guest
  ${C_CYAN}Mongo${C_RESET}       http://localhost:8088        admin/admin

Useful:  ./scripts/wsl/health.sh        — overall status
         ./scripts/wsl/logs.sh <svc>    — tail service logs
         ./scripts/wsl/restart-gateway.sh — quick gateway restart
         ./scripts/wsl/stop.sh          — shut everything down

INFO

command -v explorer.exe >/dev/null 2>&1 && \
  explorer.exe http://localhost:4200 >/dev/null 2>&1 || true
