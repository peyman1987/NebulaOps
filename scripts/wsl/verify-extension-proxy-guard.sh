#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
fail(){ echo "✗  $*" >&2; exit 1; }
[ -f frontend/dist/nebulaops/browser/extension-unavailable.html ] || fail "frontend extension unavailable page missing"
grep -q "proxy_intercept_errors on" frontend/nginx.conf || fail "nginx extension proxy fallback missing"
grep -q "error_page 502 503 504 =200 /extension-unavailable.html" frontend/nginx.conf || fail "nginx extension 502 guard missing"
grep -q "deploymentReady" backend/gateway-service/src/main/java/dev/nebulaops/gateway/api/ExtensionControlController.java || fail "gateway extension readiness guard missing"
grep -q "extensionProxyUnavailable" backend/gateway-service/src/main/java/dev/nebulaops/gateway/api/ExtensionControlController.java || fail "gateway extension unavailable response missing"
echo "✓  Extension proxy guard verified"
