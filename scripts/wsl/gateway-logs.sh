#!/usr/bin/env bash
# v24.1 — Show gateway-service startup logs and probe health.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

log_step "Gateway container status"
dc ps gateway-service

log_step "Gateway logs (last 100 lines)"
dc logs --tail=100 gateway-service

log_step "Testing gateway health directly"
curl -sv --max-time 5 http://localhost:8080/actuator/health 2>&1 | tail -15
echo
curl -sv --max-time 5 http://localhost:8080/api/health 2>&1 | tail -15
