#!/usr/bin/env bash
# v22.5 — Fast gateway restart (no-cache rebuild + restart only the gateway).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

log_step "Rebuilding gateway-service (no cache)"
dc build --no-cache gateway-service

log_step "Restarting gateway-service"
dc up -d --force-recreate --no-deps gateway-service

log_step "Waiting for health"
wait_http "http://localhost:8080/api/health" 45 "gateway-service"
