#!/usr/bin/env bash
# Rebuilds the NebulaOps v22.5 shell and ALL MFE nginx runtime images after remoteEntry/index fixes.
# This is the authoritative repair for blank MFE bodies and "Unexpected token 'export'" in remoteEntry.js.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

services=(
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
  mfe-identity-admin
  mfe-progressive-delivery
)

images=(
  nebulaops-v22-5-frontend-shell:latest
  nebulaops-v22-5-mfe-docker-desktop:latest
  nebulaops-v22-5-mfe-openlens-kubernetes:latest
  nebulaops-v22-5-mfe-task-management:latest
  nebulaops-v22-5-mfe-observability:latest
  nebulaops-v22-5-mfe-cicd-gitops:latest
  nebulaops-v22-5-mfe-terraform-studio:latest
  nebulaops-v22-5-mfe-devsecops:latest
  nebulaops-v22-5-mfe-ai-ops:latest
  nebulaops-v22-5-mfe-finops-cost:latest
  nebulaops-v22-5-mfe-infra-hub:latest
  nebulaops-v22-5-mfe-release-center:latest
  nebulaops-v22-5-mfe-policy-center:latest
  nebulaops-v22-5-mfe-notification-center:latest
  nebulaops-v22-5-mfe-identity-admin:latest
  nebulaops-v22-5-mfe-progressive-delivery:latest
)

remote_slugs=(docker-desktop openlens-kubernetes task-management observability cicd-gitops terraform-studio devsecops ai-ops finops-cost infra-hub release-center policy-center notification-center identity-admin progressive-delivery)

assert_served_remote_is_classic() {
  local slug="$1" body
  body="$(curl -fsS --max-time 8 "${NEBULAOPS_PUBLIC_URL}/remotes/${slug}/remoteEntry.js?v=v22.5.6-live-real-data" 2>/dev/null || true)"
  if [ -z "$body" ]; then
    log_err "MFE ${slug} did not serve remoteEntry.js"
    return 1
  fi
  if printf '%s' "$body" | grep -Eq '\bexport[[:space:]]+(default|\{|class|function|const|let|var)'; then
    log_err "MFE ${slug} is still serving an ESM remoteEntry.js. Browser will fail with: Unexpected token 'export'."
    return 1
  fi
  if ! printf '%s' "$body" | grep -Eq 'NebulaOps v22.5 auth bridge|nebulaopsAuthBridge'; then
    log_err "MFE ${slug} remoteEntry.js is missing the NebulaOps auth bridge. Standalone MFE API calls will not receive a Bearer token."
    return 1
  fi
  if ! printf '%s' "$body" | grep -Eq 'customElements\.define|classic standalone custom element'; then
    log_err "MFE ${slug} remoteEntry.js does not look like a shell-compatible custom element bundle"
    return 1
  fi
  log_ok "MFE ${slug} serves classic shell-compatible remoteEntry.js with auth bridge"
}

log_step "Validating local shell-compatible MFE artifacts"
"$ROOT_DIR/scripts/wsl/ensure-frontend-dist.sh"

log_step "Removing old frontend/MFE containers"
dc rm -sf "${services[@]}" >/dev/null 2>&1 || true

if [ "${NEBULAOPS_FRONTEND_CLEAN_REBUILD:-false}" = "true" ]; then
  log_step "Removing old frontend/MFE images"
  for image in "${images[@]}"; do
    docker image rm -f "$image" >/dev/null 2>&1 || true
  done
  log_step "Rebuilding frontend/MFE images without Docker cache"
  dc build --no-cache "${services[@]}"
else
  log_step "Rebuilding frontend/MFE images with Docker cache"
  dc build "${services[@]}"
fi

log_step "Starting frontend/MFE containers"
dc up -d --force-recreate "${services[@]}"

log_step "Checking MFE remoteEntry endpoints"
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

log_ok "All v22.5 shell/MFE containers were rebuilt from live-only endpoint remoteEntry artifacts"
log_info "Open ${NEBULAOPS_PUBLIC_URL}/?v=v22.5.6-live-real-data and press Ctrl+Shift+R. If Chrome still shows old JS, clear site data for nebulaops.localhost."
