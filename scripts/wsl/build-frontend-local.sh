#!/usr/bin/env bash
# NebulaOps v23.3 local frontend builder.
# Builds Angular shell and all MFE dist folders on the host/WSL only when the packaged dist artifacts are missing or invalid.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if ! command -v npm >/dev/null 2>&1; then
  echo "[ERROR] npm is required on the host/WSL to build frontend dist folders."
  exit 1
fi

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

file_has_classic_remote_contract() {
  local file="$1"
  [ -s "$file" ] || return 1
  if grep -Eq '\bexport[[:space:]]+(default|\{|class|function|const|let|var)' "$file"; then
    return 1
  fi
  grep -Eq 'NebulaOps v23.3 auth bridge|nebulaopsAuthBridge' "$file" || return 1
  grep -Eq 'customElements\.define|classic standalone custom element' "$file" || return 1
}

existing_dist_is_valid() {
  [ -s "frontend/dist/nebulaops/browser/index.html" ] || return 1
  [ -s "frontend/dist/nebulaops/browser/nebulaops-auth-bridge.js" ] || return 1
  local slug
  for slug in "${remote_slugs[@]}"; do
    [ -s "frontend/remotes/${slug}/dist/browser/index.html" ] || return 1
    [ -s "frontend/remotes/${slug}/dist/browser/nebulaops-auth-bridge.js" ] || return 1
    file_has_classic_remote_contract "frontend/remotes/${slug}/dist/browser/remoteEntry.js" || return 1
  done
}

if [ "${NEBULAOPS_FORCE_FRONTEND_DIST_BUILD:-false}" != "true" ] && existing_dist_is_valid; then
  echo "[NebulaOps] Existing v23.3 frontend/MFE dist artifacts are valid; skipping npm rebuild."
  echo "[NebulaOps] To force a full Angular rebuild, run: NEBULAOPS_FORCE_FRONTEND_DIST_BUILD=true ./scripts/wsl/build-frontend-local.sh"
  exit 0
fi

build_project() {
  local dir="$1"
  local name="$2"
  echo
  echo "==> Building ${name}"
  cd "$ROOT_DIR/$dir"

  if [ -d node_modules ] && [ "${NEBULAOPS_FORCE_NPM_CI:-false}" != "true" ]; then
    echo "[NebulaOps] Reusing existing node_modules for ${name}. Set NEBULAOPS_FORCE_NPM_CI=true to reinstall."
  elif [ -f package-lock.json ]; then
    npm ci --legacy-peer-deps --no-audit --no-fund
  else
    npm install --legacy-peer-deps --no-audit --no-fund
  fi

  npm run build
}

build_project "frontend" "frontend shell"

for remote_dir in "$ROOT_DIR"/frontend/remotes/*; do
  [ -d "$remote_dir" ] || continue
  [ -f "$remote_dir/package.json" ] || continue
  rel="${remote_dir#$ROOT_DIR/}"
  name="$(basename "$remote_dir")"
  build_project "$rel" "MFE ${name}"
done

echo
echo "[NebulaOps] Local frontend dist build completed."
echo "You can now run:"
echo "  docker compose build frontend"
echo "  ./scripts/wsl/build-frontend-images.sh"
