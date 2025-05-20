#!/usr/bin/env bash
# v21.2 — Fast gateway restart.
# Forzare sempre --no-cache per evitare immagini stale (causa principale del 502).
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

log_step "Rebuilding gateway-service (no cache — avoids stale image 502)"
dc build --no-cache gateway-service

log_step "Restarting gateway-service"
dc up -d --force-recreate --no-deps gateway-service

log_step "Waiting for health"
wait_http "http://localhost:8080/actuator/health" 60 "gateway-service" \
  || { log_warn "Gateway non risponde. Controlla i log:"; dc logs --tail=50 gateway-service; }
