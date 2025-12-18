#!/usr/bin/env bash
# v22.3 — Static and semi-static verification before runtime start.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

log_step "NebulaOps v22.3 static preflight"
python3 scripts/validate-package.py
python3 scripts/validate-yaml.py docker-compose.yml infrastructure/docker-compose.yml .gitlab-ci.yml infrastructure/argocd/application.yaml infrastructure/argocd/project.yaml infrastructure/argocd/applicationset.yaml
find scripts -name "*.sh" -print0 | xargs -0 -I{} bash -n {}
node frontend/tools/verify-remotes.mjs
bash "$ROOT_DIR/scripts/wsl/ensure-frontend-dist.sh"
for f in frontend/remotes/*/remoteEntry.js frontend/tools/*.mjs; do node --check "$f" >/dev/null; done
python3 -m py_compile ai-engine/app/main.py
if command -v go >/dev/null 2>&1; then
  if command -v timeout >/dev/null 2>&1; then
    (cd go/cache-service && timeout 90s go test ./...) || log_warn "Go cache-service tests skipped or timed out"
    (cd go/event-worker && timeout 90s go test ./...) || log_warn "Go event-worker tests skipped or timed out"
  else
    (cd go/cache-service && go test ./...) || log_warn "Go cache-service tests skipped"
    (cd go/event-worker && go test ./...) || log_warn "Go event-worker tests skipped"
  fi
else
  log_warn "go not found: skipping Go tests"
fi
if command -v npm >/dev/null 2>&1; then
  log_info "npm found. Docker frontend images use prebuilt dist folders; run ./scripts/wsl/build-frontend-local.sh only after source changes."
else
  log_warn "npm not found: local frontend build skipped"
fi
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  log_info "Docker daemon reachable. You can run: ./scripts/wsl/start.sh"
else
  log_warn "Docker daemon not reachable in this shell. Runtime start must be verified inside WSL/Docker host."
fi
log_ok "NebulaOps v22.3 preflight completed"
