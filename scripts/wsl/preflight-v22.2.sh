#!/usr/bin/env bash
# v22.2 — Static and semi-static verification before runtime start.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

log_step "NebulaOps v22.2 static preflight"
python3 scripts/validate-package.py
python3 scripts/validate-yaml.py docker-compose.yml infrastructure/docker-compose.yml .gitlab-ci.yml infrastructure/argocd/application.yaml infrastructure/argocd/project.yaml infrastructure/argocd/applicationset.yaml
find scripts -name "*.sh" -print0 | xargs -0 -I{} bash -n {}
node frontend/tools/verify-remotes.mjs
for f in frontend/remotes/*/remoteEntry.js frontend/tools/*.mjs; do node --check "$f" >/dev/null; done
python3 -m py_compile ai-engine/app/main.py
if command -v go >/dev/null 2>&1; then
  (cd go/cache-service && go test ./...)
  (cd go/event-worker && go test ./...)
else
  log_warn "go not found: skipping Go tests"
fi
if command -v npm >/dev/null 2>&1; then
  log_info "npm found. Full Angular build is executed inside Docker build; local npm build requires registry access."
else
  log_warn "npm not found: local frontend build skipped"
fi
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  log_info "Docker daemon reachable. You can run: ./scripts/wsl/start.sh"
else
  log_warn "Docker daemon not reachable in this shell. Runtime start must be verified inside WSL/Docker host."
fi
log_ok "NebulaOps v22.2 preflight completed"
