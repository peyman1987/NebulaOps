#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"
bad=0
remote="frontend/remotes/progressive-delivery"
for file in "$remote/remoteEntry.js" "$remote/dist/browser/remoteEntry.js"; do
  if [ ! -f "$file" ]; then
    log_err "Missing Progressive Delivery remote artifact: $file"
    bad=1
    continue
  fi
  if ! grep -q 'v23.1.0-live-real-data-progressive-delivery' "$file"; then
    log_err "Progressive Delivery live runtime marker missing in $file"
    bad=1
  fi
  if grep -RInE 'mock[A-Z]|mock[A-Za-z]*\(|Fallback/demo|demo records|sample records|seeded records|Local Docker/WSL|run-0094|eks-cluster' "$file" >/dev/null 2>&1; then
    log_err "Non-runtime records detected in $file"
    bad=1
  fi
done
for required in       "backend/progressive-delivery-service/src/main/java/dev/nebulaops/progressive/api/ProgressiveDeliveryController.java"       "backend/progressive-delivery-service/src/main/java/dev/nebulaops/progressive/service/ProgressiveDeliveryService.java"       "backend/progressive-delivery-service/Dockerfile"; do
  [ -f "$required" ] || { log_err "Missing $required"; bad=1; }
done
grep -q 'PROGRESSIVE_DELIVERY_SERVICE_URL' docker-compose.yml || { log_err "Gateway compose env missing PROGRESSIVE_DELIVERY_SERVICE_URL"; bad=1; }
grep -q 'progressive-delivery: ${PROGRESSIVE_DELIVERY_SERVICE_URL:http://progressive-delivery-service:8102}' backend/gateway-service/src/main/resources/application.yml || { log_err "Gateway application.yml missing proxy.progressive-delivery binding"; bad=1; }
grep -q '"/actuator/health"' backend/gateway-service/src/main/java/dev/nebulaops/gateway/config/KeycloakResourceServerConfig.java || { log_err "Gateway actuator health must be public for startup checks"; bad=1; }
grep -q '/api/progressive-delivery' backend/gateway-service/src/main/java/dev/nebulaops/gateway/api/ProxyController.java || { log_err "Gateway proxy missing progressive delivery routes"; bad=1; }
[ "$bad" -eq 0 ] || exit 1
log_ok "Progressive Delivery Center verified: runtime-only UI, API proxy and backend service are present"
