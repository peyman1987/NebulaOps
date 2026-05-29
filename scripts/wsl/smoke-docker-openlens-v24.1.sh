#!/usr/bin/env bash
# NebulaOps v24.1 — Docker Desktop/OpenLens E2E smoke.
# Opens both standalone MFEs in a real browser and checks their live backend
# contracts without executing destructive runtime actions.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/scripts/wsl/lib/smoke-runtime-lib.sh"

say "▶ NebulaOps v24.1 Docker Desktop/OpenLens smoke"
say "  baseUrl=${NEBULAOPS_BASE_URL}"

require_tool curl
require_tool python3
require_tool node

assert_static_asset_not_html() {
  local path="$1" label="$2" tmp code content_type
  tmp="$(mkbody)"
  code="$(curl -sS --max-time 15 -o "$tmp" -w '%{http_code}' -H 'Accept: application/javascript,text/javascript,*/*' "${NEBULAOPS_BASE_URL}${path}" || true)"
  content_type="$(file -b --mime-type "$tmp" 2>/dev/null || true)"
  if [[ "$code" != "200" ]]; then
    cat "$tmp" >&2 || true
    rm -f "$tmp"
    fail "$label returned HTTP $code"
  fi
  python3 - "$tmp" "$label" <<'PY'
import pathlib, sys
raw = pathlib.Path(sys.argv[1]).read_text(encoding='utf-8', errors='replace').lstrip()
label = sys.argv[2]
if raw.startswith('<!doctype') or raw.startswith('<html') or '<html' in raw[:300].lower():
    raise SystemExit(f'{label} returned HTML instead of the remote bundle')
if 'customElements.define' not in raw and 'nebulaops' not in raw.lower():
    raise SystemExit(f'{label} does not look like a NebulaOps remote bundle')
print('OK')
PY
  rm -f "$tmp"
  ok "$label is served as a JavaScript remote bundle (${content_type})"
}

say "\n1) Remote bundles must be served as JavaScript, not fallback HTML"
assert_static_asset_not_html "/remotes/docker-desktop/remoteEntry.js" "Docker Desktop remoteEntry"
assert_static_asset_not_html "/remotes/openlens-kubernetes/remoteEntry.js" "OpenLens remoteEntry"

say "\n2) Browser E2E: open Docker Desktop and OpenLens without console/runtime errors"
NEBULAOPS_UI_E2E_ROUTES="/remotes/docker-desktop/,/remotes/openlens-kubernetes/" \
  node "$ROOT_DIR/scripts/wsl/tools/e2e-ui-console-check-v24.1.mjs"

say "\n3) Docker Desktop live endpoints"
for endpoint in \
  "/api/runtime/docker/status" \
  "/api/runtime/docker/projects" \
  "/api/runtime/docker/containers" \
  "/api/runtime/docker/images" \
  "/api/runtime/docker/volumes" \
  "/api/runtime/docker/networks" \
  "/api/runtime/docker/stats" \
  "/api/runtime/docker/diagnostics" \
  "/api/runtime/docker/resource-pressure" \
  "/api/runtime/docker/project-risks" \
  "/api/runtime/docker/port-conflicts" \
  "/api/runtime/docker/topology" \
  "/api/runtime/docker/system/info" \
  "/api/runtime/docker/system/version"; do
  assert_json_endpoint GET "$endpoint" "Docker Desktop endpoint $endpoint" "" 20
done

say "\n4) OpenLens live endpoints"
for endpoint in \
  "/api/kubernetes/namespaces" \
  "/api/kubernetes/nodes" \
  "/api/kubernetes/resources?kind=pods&namespace=all" \
  "/api/kubernetes/resources?kind=deployments&namespace=all" \
  "/api/kubernetes/resources?kind=services&namespace=all" \
  "/api/kubernetes/events?namespace=all" \
  "/api/kubernetes/helm/releases?namespace=all" \
  "/api/kubernetes/network/graph?namespace=all" \
  "/api/kubernetes/security/summary?namespace=all" \
  "/api/kubernetes/service-endpoints?namespace=all" \
  "/api/kubernetes/network/connectivity-summary?namespace=all" \
  "/api/kubernetes/port-forwards" \
  "/api/kubernetes/rbac/summary?namespace=all"; do
  assert_json_endpoint GET "$endpoint" "OpenLens endpoint $endpoint" "" 20
  assert_json_body_semantics "$endpoint" "OpenLens endpoint $endpoint"
done

say "\n5) UI source live-only guard"
python3 "$ROOT_DIR/scripts/verify-live-only-runtime.py"
ok "Docker/OpenLens UI has no generated mock/static operational rows"

say "\nNebulaOps v24.1 Docker Desktop/OpenLens smoke OK"
