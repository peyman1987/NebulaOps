#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"
bad=0
for remote_dir in frontend/remotes/*; do
  [ -d "$remote_dir" ] || continue
  [ -f "$remote_dir/Dockerfile" ] || continue
  if grep -RInE 'mock[A-Z]|mock[A-Za-z]*\(|Fallback/demo|demo records|sample records|Aggiorna cert-manager|postgres_data|Optional VPS showcase|Local Docker/WSL|\$1,842|run-0094|eks-cluster' "$remote_dir/src" "$remote_dir/dist/browser" 2>/dev/null; then
    log_err "Local seeded records detected in $remote_dir"
    bad=1
  fi
  if grep -Eq '\bexport\s+(default|\{|class|function|const|let|var)' "$remote_dir/dist/browser/remoteEntry.js" 2>/dev/null; then
    log_err "ESM export syntax found in $remote_dir/dist/browser/remoteEntry.js"
    bad=1
  fi
  if ! grep -q 'v23.1.0-live-real-data' "$remote_dir/dist/browser/remoteEntry.js" 2>/dev/null; then
    log_err "Live real-data runtime marker missing in $remote_dir/dist/browser/remoteEntry.js"
    bad=1
  fi
done
if [ "$bad" -ne 0 ]; then
  log_err "Real-data mode verification failed"
  exit 1
fi
log_ok "Real-data mode verified: no local seeded MFE records and all remoteEntry bundles are live endpoint renderers"
