#!/usr/bin/env bash
# Repair only the v23.1 endpoints that can appear red after an upgrade from an earlier package:
# - MFE INFRA, Release, Policy, Notifications
# - Cost analytics service
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

release_port_if_nebulaops() {
  local port="$1" purpose="$2" rows id name project service
  rows=$(docker ps --filter "publish=$port" \
    --format '{{.ID}}|{{.Names}}|{{.Label "com.docker.compose.project"}}|{{.Label "com.docker.compose.service"}}' 2>/dev/null || true)
  [ -z "$rows" ] && return 0
  while IFS='|' read -r id name project service; do
    [ -z "$id" ] && continue
    case "$project" in
      nebulaops|nebulaops-*|nebulaops_v*|nebulaops-v*|"$PROJECT_NAME")
        log_warn "Removing stale NebulaOps container on port $port: $name ($service)"
        docker rm -f "$id" >/dev/null 2>&1 || true
        ;;
      *)
        log_err "Port $port is used by non-NebulaOps container '$name'. Stop it before repairing $purpose."
        exit 1
        ;;
    esac
  done <<< "$rows"
}

log_step "Validating frontend runtime artifacts"
"$ROOT_DIR/scripts/wsl/ensure-frontend-dist.sh"

log_step "Releasing v23.1 red endpoint ports"
for port in 8097; do
  release_port_if_nebulaops "$port" "v23.1 red endpoint repair"
done

log_step "Ensuring backend JARs for Cost analytics"
"$ROOT_DIR/scripts/wsl/build-backend-jars.sh"

log_step "Cost analytics rebuild"
dc rm -sf cost-analytics-service >/dev/null 2>&1 || true
dc build cost-analytics-service
dc up -d cost-analytics-service

log_step "Recreating v23.1 extended micro frontends"
dc build \
  mfe-infra-hub \
  mfe-release-center \
  mfe-policy-center \
  mfe-notification-center \
  mfe-identity-admin \
  mfe-progressive-delivery
dc up -d \
  mfe-infra-hub \
  mfe-release-center \
  mfe-policy-center \
  mfe-notification-center \
  mfe-identity-admin \
  mfe-progressive-delivery

log_step "Waiting for repaired endpoints"
wait_http "${NEBULAOPS_PUBLIC_URL}/remotes/infra-hub/remoteEntry.js" 60 "MFE INFRA"
wait_http "${NEBULAOPS_PUBLIC_URL}/remotes/release-center/remoteEntry.js" 60 "MFE Release"
wait_http "${NEBULAOPS_PUBLIC_URL}/remotes/policy-center/remoteEntry.js" 60 "MFE Policy"
wait_http "${NEBULAOPS_PUBLIC_URL}/remotes/notification-center/remoteEntry.js" 60 "MFE Notifications"
wait_http "${NEBULAOPS_PUBLIC_URL}/remotes/identity-admin/remoteEntry.js" 60 "MFE Identity"
wait_http "${NEBULAOPS_PUBLIC_URL}/remotes/progressive-delivery/remoteEntry.js" 60 "MFE Progressive"
wait_http "http://localhost:8097/actuator/health" 120 "Cost analytics" || { log_err "Cost analytics did not start. Recent logs:"; dc logs --tail=120 cost-analytics-service || true; exit 1; }


log_step "Checking MFE runtime bundles through reverse proxy"
"$ROOT_DIR/scripts/wsl/health.sh" || true

log_ok "v23.1 red endpoints repaired"
echo "Run: ./scripts/wsl/health.sh"
