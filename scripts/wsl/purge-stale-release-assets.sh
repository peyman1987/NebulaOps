#!/usr/bin/env bash
# v23.4 — remove known stale release assets left behind when a new archive is extracted over an old workspace.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

PREVIOUS_DOT="23"".""3"
PREVIOUS_DASH="23""-""3"
PREVIOUS_UNDER="V23""_""3"

KNOWN_STALE_FILES=(
  "frontend/src/assets/nebulaops-v22-3-shell-compat.js"
  "frontend/src/assets/nebulaops-v22-5-shell-compat.js"
  "docs/diagrams/nebulaops-v22-3-reverse-proxy-runtime.svg"
  "docker-compose.v22.5.override.yml"
)

removed=0
for f in "${KNOWN_STALE_FILES[@]}"; do
  if [[ -f "$f" ]]; then
    rm -f "$f"
    log_warn "Removed stale release artifact: $f"
    removed=1
  fi
done

while IFS= read -r -d '' stale; do
  rm -f "$stale"
  log_warn "Removed stale previous-minor release artifact: ${stale#./}"
  removed=1
done < <(find frontend/src/assets frontend/dist/nebulaops/browser/assets docs/diagrams infrastructure/observability scripts reports -maxdepth 2 -type f   \( -name "*${PREVIOUS_DOT}*" -o -name "*${PREVIOUS_DASH}*" -o -name "*${PREVIOUS_UNDER}*" \) -print0 2>/dev/null)

if [[ -d docs/final ]]; then
  while IFS= read -r -d '' f; do
    rm -f "$f"
    log_warn "Removed stale generated v22 document: ${f#./}"
    removed=1
  done < <(find docs/final -maxdepth 1 -type f -iname 'NebulaOps_v22*.pdf' -print0)
fi

if [[ -f .kube/config ]]; then
  if grep -qE 'server: https://172\.|client-key-data:|client-certificate-data:' .kube/config 2>/dev/null; then
    rm -f .kube/config
    log_warn "Removed packaged/local kubeconfig; it will be regenerated from the operator environment at startup"
    removed=1
  fi
fi

if [[ "$removed" -eq 0 ]]; then
  log_ok "No stale v22 release artifacts found"
fi
