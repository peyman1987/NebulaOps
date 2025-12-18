#!/usr/bin/env bash
# Rebuild frontend/MFE containers and verify the standalone MFE auth bridge.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

ports=(4211 4212 4213 4214 4215 4216 4217 4218 4219 4220 4221 4222 4223)

log_step "Rebuilding all frontend/MFE runtime containers with the v22.3 auth bridge"
"$ROOT_DIR/scripts/wsl/repair-v22.3-frontend-remotes.sh"

check_auth_bridge_on_port() {
  local port="$1" body token
  body="$(curl -fsS --max-time 8 "http://localhost:${port}/remoteEntry.js?v=v22.3.6-live-real-data" 2>/dev/null || true)"
  if [ -z "$body" ]; then
    log_err "MFE ${port} did not serve remoteEntry.js"
    return 1
  fi
  if ! printf '%s' "$body" | grep -Eq 'NebulaOps v22.3 auth bridge|nebulaopsAuthBridge'; then
    log_err "MFE ${port} is still serving a remoteEntry.js without auth bridge"
    return 1
  fi
  token="$(curl -fsS --max-time 8 \
    -H 'Content-Type: application/json' \
    -d '{"email":"admin","password":"admin"}' \
    "http://localhost:${port}/api/auth/login" 2>/dev/null | grep -Eo '"accessToken"[[:space:]]*:[[:space:]]*"[^"]+"' | head -1 || true)"
  if [ -z "$token" ]; then
    log_err "MFE ${port} cannot bootstrap a token through same-origin /api/auth/login"
    return 1
  fi
  log_ok "MFE ${port} has auth bridge and same-origin token bootstrap"
}

log_step "Checking standalone MFE token bootstrap"
failed=0
for port in "${ports[@]}"; do
  check_auth_bridge_on_port "$port" || failed=1
done

if [ "$failed" -ne 0 ]; then
  log_err "Standalone MFE auth bridge verification failed"
  exit 1
fi

cat <<'MSG'

Standalone MFE auth is repaired.
Open the browser in a fresh tab or incognito and use:
  http://nebulaops.localhost/?v=v22.3.6-live-real-data

In DevTools > Network, requests to /api/** from nebulaops.localhost/remotes/<module> should include:
  Authorization: Bearer ...
  X-NebulaOps-Auth-Bridge: v22.3.6-live-real-data

If Chrome still shows old requests, clear site data for localhost or run:
  ./scripts/wsl/clear-v22.3-browser-cache-note.sh
MSG
