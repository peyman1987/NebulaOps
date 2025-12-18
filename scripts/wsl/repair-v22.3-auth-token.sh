#!/usr/bin/env bash
# Rebuild the v22.3 dual-JWT authentication bridge.
# Use this when standalone MFE pages return 401 with "Another algorithm expected"
# or when Chrome keeps an old Bearer token in localStorage.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

PROJECT_NAME="${COMPOSE_PROJECT_NAME:-nebulaops-v22-3}"
COMPOSE=(docker compose -p "$PROJECT_NAME" -f docker-compose.yml)

backend_services=(
  auth-service
  task-service
  notification-service
  file-service
  ai-ops-service
  devsecops-service
  pipeline-engine-service
  observability-service
  gitops-control-service
  environment-manager-service
  terraform-studio-service
  gateway-service
  cost-analytics-service
  release-orchestrator-service
  policy-governance-service
  audit-service
)

frontend_services=(
  frontend
  mfe-docker-desktop
  mfe-openlens-kubernetes
  mfe-task-management
  mfe-observability
  mfe-cicd-gitops
  mfe-terraform-studio
  mfe-devsecops
  mfe-ai-ops
  mfe-finops-cost
  mfe-infra-hub
  mfe-release-center
  mfe-policy-center
  mfe-notification-center
)

log_step "Building backend JARs with dual JWT decoder"
"$ROOT_DIR/scripts/wsl/build-backend-jars.sh" --force

log_step "Rebuilding backend images that validate Bearer tokens"
"${COMPOSE[@]}" build "${backend_services[@]}"

log_step "Rebuilding shell and MFE static images with refreshed auth bridge"
"${COMPOSE[@]}" build "${frontend_services[@]}"

log_step "Recreating affected containers"
"${COMPOSE[@]}" up -d "${backend_services[@]}" "${frontend_services[@]}"

log_ok "v22.3 auth token bridge repaired"
echo "Open http://nebulaops.localhost/?v=v22.3.8-dual-jwt-auth-bridge and run Ctrl+Shift+R."
echo "If Chrome still sends an old token, clear site data for localhost or run an Incognito window."
