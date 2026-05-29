#!/usr/bin/env bash
# NebulaOps v24.1 compile readiness guard.
# Non-destructive and intentionally fast: verifies the build inputs that most
# often break compilation/startup before a full rebuild.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/scripts/wsl/lib/common.sh"
cd "$ROOT_DIR"

fail=0
report="$ROOT_DIR/reports/compile-readiness-v24.1.json"
mkdir -p "$ROOT_DIR/reports" "$ROOT_DIR/.build/logs"

log_step "Checking v24.1 compile inputs"
check() {
  local label="$1"; shift
  if "$@"; then log_ok "$label"; else log_err "$label"; fail=1; fi
}

check "package validation" python3 scripts/validate-package.py
check "version alignment" python3 scripts/validate-version-alignment-v24.1.py
check "live-only runtime guard" python3 scripts/verify-live-only-runtime.py
check "YAML syntax" python3 scripts/validate-yaml.py docker-compose.yml infrastructure/docker-compose.yml .gitlab-ci.yml infrastructure/argocd/application.yaml infrastructure/argocd/project.yaml infrastructure/argocd/applicationset.yaml infrastructure/kubernetes/apiforge.yaml extensions/apiforge/k8s/deployment.yml extensions/contract-hub/k8s/deployment.yml extensions/kubebridge/k8s/deployment.yml

log_step "Checking critical shell scripts"
for file in \
  scripts/wsl/start.sh \
  scripts/wsl/preflight-v24.1.sh \
  scripts/wsl/build-backend-jars.sh \
  scripts/wsl/build-frontend-changed.sh \
  scripts/wsl/build-frontend-local.sh \
  scripts/wsl/compile-v24.1.sh \
  scripts/wsl/verify-compile-readiness-v24.1.sh; do
  bash -n "$file" || fail=1
done

log_step "Checking JavaScript build/runtime scripts"
for file in \
  frontend/tools/build-changed-remotes.mjs \
  frontend/tools/build-all-remotes.mjs \
  frontend/tools/generate-live-only-remote.cjs \
  frontend/tools/live-only-remote.template.js \
  frontend/tools/verify-frontend-architecture-v24.1.mjs \
  frontend/tools/verify-remotes.mjs \
  frontend/src/assets/nebulaops-v24-1-shell-compat.js \
  frontend/src/assets/nebulaops-extension-control-panel.js; do
  node --check "$file" || fail=1
done
find frontend/remotes -maxdepth 2 -type f -name 'build-remote-entry.cjs' -print0 | xargs -0 -r -n1 node --check || fail=1
find frontend/remotes -maxdepth 4 -type f -path '*/dist/browser/remoteEntry.js' -print0 | xargs -0 -r -n1 node --check || fail=1

log_step "Checking frontend dependency state"
if [ ! -d frontend/node_modules ] || [ "${NEBULAOPS_FORCE_NPM_CI:-false}" = "true" ]; then
  log_warn "frontend/node_modules missing or reinstall forced; npm ci will run during compile"
else
  log_ok "frontend/node_modules present"
fi
node -e 'const p=require("./frontend/package.json"), l=require("./frontend/package-lock.json"); if(p.version!=="24.1.0"||l.version!=="24.1.0") process.exit(1);' || fail=1

log_step "Checking backend builder availability"
host_maven=false
docker_available=false
if command -v javac >/dev/null 2>&1; then
  javac -version 2>&1 | tee "$ROOT_DIR/.build/logs/javac-version-v24.1.log" >/dev/null
  log_ok "JDK detected"
else
  log_warn "javac not found in WSL PATH"
fi
if command -v mvn >/dev/null 2>&1 && mvn -version >/dev/null 2>&1; then
  host_maven=true
  log_ok "Host Maven detected"
elif command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  docker_available=true
  log_ok "Dockerized Maven fallback available"
else
  log_warn "No host Maven and no reachable Docker daemon detected in this shell"
  log_warn "Backend compile can pass in WSL after installing Maven or starting Docker Desktop"
fi

cat > "$report" <<JSON
{
  "release": "v24.1",
  "version": "24.1.0",
  "project": "nebulaops-v24-1",
  "ok": $([ "$fail" -eq 0 ] && echo true || echo false),
  "hostMaven": ${host_maven},
  "dockerAvailable": ${docker_available},
  "generatedAt": "$(date -Is)"
}
JSON

[ "$fail" -eq 0 ] || exit 1
log_ok "v24.1 compile readiness completed"
