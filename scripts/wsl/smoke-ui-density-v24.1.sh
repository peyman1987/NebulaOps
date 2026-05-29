#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

ok() {
  echo "OK: $*"
}

[[ -f frontend/src/assets/nebulaops-density-v24-1.css ]] || fail "source density stylesheet missing"
[[ -f frontend/dist/nebulaops/browser/assets/nebulaops-density-v24-1.css ]] || fail "dist density stylesheet missing"
grep -q 'nebulaops-density-v24-1.css' frontend/src/index.html || fail "source index does not load density stylesheet"
grep -q 'nebulaops-density-v24-1.css' frontend/dist/nebulaops/browser/index.html || fail "dist index does not load density stylesheet"
ok "shell density stylesheet is present and loaded"

grep -R 'v24.1 Priority 7 density mode' frontend/tools/live-only-remote.template.js frontend/remotes/*/remoteEntry.js >/dev/null || fail "remote density marker missing"
ok "remote density CSS is embedded in generated remote entries"

if grep -R '<details open><summary class="muted small">Raw endpoint response' frontend/tools/live-only-remote.template.js frontend/remotes/*/remoteEntry.js >/dev/null; then
  fail "raw endpoint response is still open by default"
fi
ok "raw endpoint responses are closed by default"

grep -R 'detail-drawer' frontend/tools/live-only-remote.template.js frontend/remotes/*/remoteEntry.js >/dev/null || fail "detail drawer markup/CSS missing"
ok "detail panel drawer is available"

grep -q 'compact extension control plane density' frontend/src/assets/nebulaops-extension-control-panel.js || fail "extension panel source density missing"
grep -q 'compact extension control plane density' frontend/dist/nebulaops/browser/assets/nebulaops-extension-control-panel.js || fail "extension panel dist density missing"
ok "extension control panel density is present"

if command -v node >/dev/null 2>&1; then
  node --check frontend/remotes/operational-issues/remoteEntry.js
  node --check frontend/remotes/docker-desktop/remoteEntry.js
  node --check frontend/remotes/openlens-kubernetes/remoteEntry.js
  node --check frontend/src/assets/nebulaops-extension-control-panel.js
  ok "representative JavaScript files parse correctly"
else
  echo "WARN: node not found; skipping JS parse checks"
fi

echo "NebulaOps v24.1 UI density smoke test passed."
