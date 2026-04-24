#!/usr/bin/env bash
# v23.2 — Fast static verification before runtime start.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

log_step "NebulaOps v23.2 static preflight"
"$ROOT_DIR/scripts/wsl/purge-stale-release-assets.sh"
python3 scripts/validate-package.py
python3 scripts/verify-live-only-runtime.py
python3 scripts/validate-yaml.py docker-compose.yml infrastructure/docker-compose.yml .gitlab-ci.yml infrastructure/argocd/application.yaml infrastructure/argocd/project.yaml infrastructure/argocd/applicationset.yaml infrastructure/kubernetes/apiforge.yaml extensions/*/k8s/deployment.yml
while IFS= read -r -d "" script_file; do
  bash -n "$script_file" < /dev/null
done < <(find scripts -name "*.sh" -print0)
node frontend/tools/verify-remotes.mjs
bash "$ROOT_DIR/scripts/wsl/ensure-frontend-dist.sh"
for f in frontend/remotes/*/remoteEntry.js frontend/tools/*.mjs frontend/tools/*.js; do
  [[ -f "$f" ]] && node --check "$f" >/dev/null
done
python3 -m py_compile ai-engine/app/main.py
log_info "Extension Maven/POM deep guard is available separately: ./scripts/wsl/verify-extensions-compile-inputs.sh"
log_info "Go tests are skipped by default in preflight. Set NEBULAOPS_RUN_GO_TESTS=true and run go test manually when needed."
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  log_info "Docker daemon reachable. You can run: ./scripts/wsl/start.sh"
else
  log_warn "Docker daemon not reachable in this shell. Runtime start must be verified inside WSL/Docker host."
fi
log_ok "NebulaOps v23.2 preflight completed"
