#!/usr/bin/env bash
# Hard reset for stale shell/MFE remoteEntry.js images.
# Use this when /remotes/<slug>/remoteEntry.js is reachable but misses the
# NebulaOps auth bridge or still serves old/stale JavaScript.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

log_step "Hard-resetting NebulaOps v23.2 frontend/MFE runtime"
export NEBULAOPS_FORCE_NO_CACHE_MFE_IMAGES=true
export NEBULAOPS_CACHE_BUSTER="v23.2-authbridge-$(date +%Y%m%d%H%M%S)"

log_info "Stopping current compose frontend/MFE services"
dc rm -sf frontend mfe-platform-catalog mfe-incident-command-center mfe-runtime-readiness \
  mfe-docker-storage-cleanup mfe-environment-configuration mfe-dependency-impact \
  mfe-test-quality-dashboard mfe-docker-desktop mfe-openlens-kubernetes mfe-task-management \
  mfe-observability mfe-cicd-gitops mfe-terraform-studio mfe-devsecops mfe-ai-ops \
  mfe-finops-cost mfe-infra-hub mfe-release-center mfe-policy-center mfe-notification-center \
  mfe-identity-admin mfe-progressive-delivery >/dev/null 2>&1 || true

log_info "Removing old NebulaOps frontend/MFE containers from previous project names"
docker ps -aq --filter "name=nebulaops" | xargs -r docker inspect \
  --format '{{.Id}}|{{.Name}}|{{ index .Config.Labels "com.docker.compose.service" }}' 2>/dev/null \
  | while IFS='|' read -r id name service; do
      case "${service:-}" in
        frontend|mfe-*)
          log_warn "Removing stale frontend/MFE container ${name#/} (${service})"
          docker rm -f "$id" >/dev/null 2>&1 || true
          ;;
      esac
    done

log_info "Removing stale NebulaOps v23.2 frontend/MFE images"
docker image ls --format '{{.Repository}}:{{.Tag}}' \
  | grep -E '^nebulaops-v23-2-(frontend-shell|mfe-)' \
  | xargs -r docker image rm -f >/dev/null 2>&1 || true

log_step "Validating packaged frontend/MFE dist before rebuild"
"$ROOT_DIR/scripts/wsl/ensure-frontend-dist.sh"

log_step "Rebuilding and verifying frontend/MFE runtime"
"$ROOT_DIR/scripts/wsl/repair-v23.2-frontend-remotes.sh"
