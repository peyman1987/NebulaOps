#!/usr/bin/env bash
# NebulaOps v23.2 frontend runtime guard.
# Docker frontend/MFE images are nginx runtime images and therefore require prebuilt dist folders.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

missing=0
check_file() {
  local path="$1"
  if [ ! -f "$path" ]; then
    log_err "Missing frontend build artifact: $path"
    missing=1
  fi
}

check_file "frontend/dist/nebulaops/browser/index.html"
check_file "frontend/dist/nebulaops/browser/nebulaops-auth-bridge.js"
for remote_dir in frontend/remotes/*; do
  [ -d "$remote_dir" ] || continue
  [ -f "$remote_dir/Dockerfile" ] || continue
  check_file "$remote_dir/dist/browser/remoteEntry.js"
  check_file "$remote_dir/dist/browser/index.html"
  check_file "$remote_dir/dist/browser/nebulaops-auth-bridge.js"
  if ! grep -Eq 'NebulaOps v23.2 auth bridge|nebulaopsAuthBridge' "$remote_dir/dist/browser/remoteEntry.js" 2>/dev/null; then
    log_err "Missing auth bridge in $remote_dir/dist/browser/remoteEntry.js. Standalone MFE API calls will not receive Authorization Bearer tokens."
    missing=1
  fi
  if grep -Eq '\bexport\s+(default|\{|class|function|const|let|var)' "$remote_dir/dist/browser/remoteEntry.js" 2>/dev/null; then
    log_err "Invalid ESM syntax in $remote_dir/dist/browser/remoteEntry.js. Shell-loaded remoteEntry.js must be classic JavaScript."
    missing=1
  fi
  if grep -Eq 'type=["'"'"']module["'"'"']' "$remote_dir/dist/browser/index.html" 2>/dev/null; then
    log_err "Invalid module script in $remote_dir/dist/browser/index.html. Standalone MFE must load classic remoteEntry.js."
    missing=1
  fi
done

if [ "$missing" -ne 0 ]; then
  log_err "Frontend dist artifacts are incomplete or shell-incompatible. Run: ./scripts/wsl/repair-v23.2-frontend-remotes.sh"
  exit 1
fi

node frontend/tools/verify-remotes.mjs
log_ok "Frontend shell and all MFE dist artifacts are present and shell-compatible"
