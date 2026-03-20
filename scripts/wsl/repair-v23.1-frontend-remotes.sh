#!/usr/bin/env bash
# NebulaOps v23.1 frontend/MFE runtime repair.
# Rebuilds local Angular/custom-element dist artifacts, rebuilds nginx runtime
# images without cache, recreates containers, and verifies that the shell serves
# classic same-origin remoteEntry.js bundles for every MFE.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

services=(
  frontend
  mfe-platform-catalog
  mfe-incident-command-center
  mfe-runtime-readiness
  mfe-docker-storage-cleanup
  mfe-environment-configuration
  mfe-dependency-impact
  mfe-test-quality-dashboard
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
  mfe-identity-admin
  mfe-progressive-delivery
)

remote_slugs=(
  platform-catalog
  incident-command-center
  runtime-readiness
  docker-storage-cleanup
  environment-configuration
  dependency-impact
  test-quality-dashboard
  docker-desktop
  openlens-kubernetes
  task-management
  observability
  cicd-gitops
  terraform-studio
  devsecops
  ai-ops
  finops-cost
  infra-hub
  release-center
  policy-center
  notification-center
  identity-admin
  progressive-delivery
)

image_for_service() {
  local svc="$1"
  if [ "$svc" = "frontend" ]; then
    printf 'nebulaops-v23-1-frontend-shell:latest\n'
  else
    printf 'nebulaops-v23-1-%s:latest\n' "$svc"
  fi
}

assert_local_remote_is_classic() {
  local slug="$1" file="frontend/remotes/${slug}/dist/browser/remoteEntry.js"
  if [ ! -f "$file" ]; then
    log_err "Missing local dist bundle: $file"
    return 1
  fi
  if grep -Eq '\bexport[[:space:]]+(default|\{|class|function|const|let|var)' "$file"; then
    log_err "Invalid ESM syntax in local bundle: $file"
    return 1
  fi
  if ! grep -Eq 'NebulaOps v23.1 auth bridge|nebulaopsAuthBridge' "$file"; then
    log_err "Missing auth bridge in local bundle: $file"
    return 1
  fi
  if ! grep -Eq 'customElements\.define|classic standalone custom element' "$file"; then
    log_err "Local bundle is not a shell-compatible custom element: $file"
    return 1
  fi
}

assert_served_remote_is_classic() {
  local slug="$1" body
  body="$(curl -fsS --max-time 8 "${NEBULAOPS_PUBLIC_URL}/remotes/${slug}/remoteEntry.js?v=$(date +%s)" 2>/dev/null || true)"
  if [ -z "$body" ]; then
    log_err "MFE ${slug} did not serve remoteEntry.js from the frontend shell"
    return 1
  fi
  if printf '%s' "$body" | grep -Eq '\bexport[[:space:]]+(default|\{|class|function|const|let|var)'; then
    log_err "MFE ${slug} is still serving an ESM remoteEntry.js. Browser will fail with: Unexpected token 'export'."
    return 1
  fi
  if ! printf '%s' "$body" | grep -Eq 'NebulaOps v23.1 auth bridge|nebulaopsAuthBridge'; then
    log_err "MFE ${slug} remoteEntry.js is missing the NebulaOps auth bridge."
    return 1
  fi
  if ! printf '%s' "$body" | grep -Eq 'customElements\.define|classic standalone custom element'; then
    log_err "MFE ${slug} remoteEntry.js is not a shell-compatible custom element bundle."
    return 1
  fi
  log_ok "MFE ${slug} serves classic shell-compatible remoteEntry.js"
}

log_step "Building local frontend shell and MFE dist artifacts"
if [ "${NEBULAOPS_SKIP_FRONTEND_DIST_BUILD:-false}" = "true" ]; then
  log_warn "Skipping local frontend dist build because NEBULAOPS_SKIP_FRONTEND_DIST_BUILD=true"
else
  "$ROOT_DIR/scripts/wsl/build-frontend-local.sh"
fi

log_step "Validating local shell-compatible MFE artifacts"
"$ROOT_DIR/scripts/wsl/ensure-frontend-dist.sh"
for slug in "${remote_slugs[@]}"; do
  assert_local_remote_is_classic "$slug"
done

log_step "Removing stale frontend/MFE containers"
dc rm -sf "${services[@]}" >/dev/null 2>&1 || true

log_step "Removing stale frontend/MFE images"
for svc in "${services[@]}"; do
  docker image rm -f "$(image_for_service "$svc")" >/dev/null 2>&1 || true
done

log_step "Rebuilding frontend/MFE runtime images without Docker cache"
dc build --no-cache "${services[@]}"

log_step "Starting frontend/MFE containers from rebuilt images"
dc up -d --force-recreate "${services[@]}"

log_step "Checking same-origin MFE remoteEntry endpoints"
wait_http "${NEBULAOPS_PUBLIC_URL}/" 90 "frontend shell"
failed=0
for slug in "${remote_slugs[@]}"; do
  wait_http "${NEBULAOPS_PUBLIC_URL}/remotes/${slug}/remoteEntry.js" 90 "MFE ${slug}" || failed=1
  assert_served_remote_is_classic "$slug" || failed=1
done

if [ "$failed" -ne 0 ]; then
  log_err "One or more MFE runtime bundles are still invalid. Inspect with: curl ${NEBULAOPS_PUBLIC_URL}/remotes/<slug>/remoteEntry.js | head"
  exit 1
fi

log_ok "All NebulaOps v23.1 MFE runtime bundles are rebuilt and shell-compatible"
log_info "Open ${NEBULAOPS_PUBLIC_URL}/?v=v23.1.0-live-real-data and press Ctrl+Shift+R. If Chrome still shows old JavaScript, clear site data for nebulaops.localhost."
