#!/usr/bin/env bash
# v23.1 — Disable/clean optional GitLab CE from the local NebulaOps runtime.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

log_step "Disabling optional GitLab CE runtime"
ids="$(docker ps -aq \
  --filter "label=com.docker.compose.project=$PROJECT_NAME" \
  --filter "label=com.docker.compose.service=gitlab" 2>/dev/null || true)"
if [ -n "$ids" ]; then
  log_warn "Removing GitLab container(s)"
  docker rm -f $ids >/dev/null 2>&1 || true
else
  log_ok "No GitLab container is running for project $PROJECT_NAME"
fi

log_info "GitLab image/volumes are left intact. To remove GitLab data too, run:"
log_info "docker volume rm ${PROJECT_NAME}_gitlab-config ${PROJECT_NAME}_gitlab-logs ${PROJECT_NAME}_gitlab-data"
log_ok "GitLab is disabled by default. Start with --with-gitlab only when needed."
