#!/usr/bin/env bash
# v23.4 — release identity smoke guard for docs, package metadata, Maven, Docker and frontend runtime assets.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

log_step "Checking NebulaOps v23.4 release identity alignment"
python3 scripts/validate-version-alignment-v23.4.py

log_step "Checking start-script referenced v23.4 helpers"
required=(
  scripts/wsl/preflight-v23.4.sh
  scripts/wsl/repair-v23.4-frontend-remotes.sh
  scripts/wsl/repair-v23.4-docker-context.sh
  scripts/wsl/sync-v23.4-frontend-runtime.sh
  scripts/wsl/reset-v23.4-frontend-runtime.sh
  scripts/hotfix-v23.4-nginx-remote-routing.sh
  frontend/src/assets/nebulaops-v23-4-shell-compat.js
  frontend/dist/nebulaops/browser/assets/nebulaops-v23-4-shell-compat.js
  docs/diagrams/nebulaops-v23-4-reverse-proxy-runtime.svg
  infrastructure/observability/V23_4_OBSERVABILITY_NOTE.md
  reports/preflight-v23.4.json
)
for f in "${required[@]}"; do
  [[ -f "$f" ]] || log_err "Missing v23.4 aligned file: $f"
done

log_step "Checking stale physical filenames from the previous minor release are absent"
OLD_DOT="23"".""3"
OLD_DASH="23""-""3"
OLD_UNDER="V23""_""3"
if find . -path './node_modules' -prune -o -path './.git' -prune -o -path './target' -prune -o \( -iname "*${OLD_DOT}*" -o -iname "*${OLD_DASH}*" -o -iname "*${OLD_UNDER}*" \) -print | grep -q .; then
  find . -path './node_modules' -prune -o -path './.git' -prune -o -path './target' -prune -o \( -iname "*${OLD_DOT}*" -o -iname "*${OLD_DASH}*" -o -iname "*${OLD_UNDER}*" \) -print
  log_err "Stale previous-minor release filenames found"
fi

log_ok "NebulaOps v23.4 release identity alignment verified"
