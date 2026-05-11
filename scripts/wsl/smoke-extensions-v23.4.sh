#!/usr/bin/env bash
# NebulaOps v23.4 — async Extension Control Plane E2E smoke.
# Verifies that registry calls do not block/504 and that start returns 202 with
# an operationId. This smoke starts a real extension operation unless explicitly skipped.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/scripts/wsl/lib/smoke-runtime-lib.sh"

say "▶ NebulaOps v23.4 extensions E2E smoke"
say "  baseUrl=${NEBULAOPS_BASE_URL}"
say "  extension=${NEBULAOPS_EXTENSION_SMOKE_SLUG}"

require_tool curl
require_tool python3

fetch_json_to_file() {
  local method="$1" path="$2" body="${3:-}" expected_code="${4:-}" timeout="${5:-20}"
  local meta code content_type elapsed file
  IFS='|' read -r code content_type elapsed file < <(http_request "$method" "$path" "$body" "$timeout")
  if [[ "$code" == "504" ]]; then
    cat "$file" >&2 || true
    rm -f "$file"
    fail "$method $path returned HTTP 504"
  fi
  if [[ -n "$expected_code" && "$code" != "$expected_code" ]]; then
    cat "$file" >&2 || true
    rm -f "$file"
    fail "$method $path returned HTTP $code, expected $expected_code"
  fi
  python3 - "$file" "$method $path" <<'PY'
import json, pathlib, sys
raw=pathlib.Path(sys.argv[1]).read_text(encoding='utf-8', errors='replace').strip()
label=sys.argv[2]
if raw.startswith('<') or '<html' in raw[:300].lower():
    raise SystemExit(f'{label} returned HTML instead of JSON')
json.loads(raw)
PY
  printf '%s\n' "$file"
}

say "\n1) Extension registry must be fast, JSON-only and live-only"
assert_status_under GET "/api/extensions" "extension registry timeout guard" 10
registry_file="$(fetch_json_to_file GET "/api/extensions" "" "200" 10)"
assert_json_field "$registry_file" realDataOnly >/dev/null
assert_json_field "$registry_file" mode >/dev/null
python3 - "$registry_file" "$NEBULAOPS_EXTENSION_SMOKE_SLUG" <<'PY'
import json, sys
payload=json.load(open(sys.argv[1], encoding='utf-8'))
slug=sys.argv[2]
values=payload.get('items') or []
if not any(str(row.get('id') or row.get('slug')) == slug for row in values if isinstance(row, dict)):
    raise SystemExit(f'extension registry does not include {slug}')
PY
rm -f "$registry_file"
ok "extension registry exposes installed extension metadata without blocking"

say "\n2) Events and diagnostics endpoints must be reachable before start"
assert_json_endpoint GET "/api/extensions/${NEBULAOPS_EXTENSION_SMOKE_SLUG}/events" "extension events" "" 10
assert_json_endpoint GET "/api/extensions/${NEBULAOPS_EXTENSION_SMOKE_SLUG}/diagnostics" "extension diagnostics" "" 20

if [[ "${NEBULAOPS_SKIP_EXTENSION_START_SMOKE:-0}" == "1" ]]; then
  warn "Skipping POST /api/extensions/${NEBULAOPS_EXTENSION_SMOKE_SLUG}/start because NEBULAOPS_SKIP_EXTENSION_START_SMOKE=1"
  say "NebulaOps v23.4 extensions smoke OK"
  exit 0
fi

say "\n3) Start must be asynchronous: 202 Accepted + operationId"
start_file="$(fetch_json_to_file POST "/api/extensions/${NEBULAOPS_EXTENSION_SMOKE_SLUG}/start" '{}' "202" 15)"
operation_id="$(assert_json_field "$start_file" operationId)"
operation_url="$(assert_json_field "$start_file" operationUrl)"
phase="$(assert_json_field "$start_file" phase)"
state="$(assert_json_field "$start_file" state)"
python3 - "$start_file" <<'PY'
import json, sys
payload=json.load(open(sys.argv[1], encoding='utf-8'))
if not payload.get('operationId'):
    raise SystemExit('start response missing operationId')
if not payload.get('accepted'):
    raise SystemExit('start response missing accepted=true')
if str(payload.get('phase') or '').upper() not in {'STARTING','PULLING_IMAGE','APPLYING_MANIFESTS','WAITING_FOR_ROLLOUT','PROBING_ENDPOINT','READY','FAILED','TIMEOUT'}:
    raise SystemExit('start response has unexpected phase')
PY
rm -f "$start_file"
ok "start accepted with operationId=${operation_id} phase=${phase} state=${state}"

say "\n4) Operation polling and events must expose progress as JSON"
operation_file="$(fetch_json_to_file GET "$operation_url" "" "200" 10)"
assert_json_field "$operation_file" operationId >/dev/null
assert_json_field "$operation_file" phase >/dev/null
assert_json_field "$operation_file" eventsUrl >/dev/null
rm -f "$operation_file"
ok "operation status endpoint is reachable"

found_event=0
for attempt in $(seq 1 6); do
  events_file="$(fetch_json_to_file GET "/api/extensions/${NEBULAOPS_EXTENSION_SMOKE_SLUG}/events" "" "200" 10)"
  if python3 - "$events_file" "$operation_id" <<'PY'
import json, sys
payload=json.load(open(sys.argv[1], encoding='utf-8'))
operation_id=sys.argv[2]
events=payload.get('items') or []
if any(str(event.get('operationId')) == operation_id for event in events if isinstance(event, dict)):
    pass
else:
    raise SystemExit(1)
PY
  then
    rm -f "$events_file"
    found_event=1
    ok "events stream contains operation ${operation_id}"
    break
  fi
  rm -f "$events_file"
  sleep 2
done
if [[ "$found_event" != "1" ]]; then
  fail "events stream did not include operation ${operation_id}"
fi

say "\n5) Existing async smoke remains available for deeper terminal-state polling"
bash -n "$ROOT_DIR/scripts/wsl/smoke-extension-async-control-plane-v23.4.sh"
ok "legacy async extension smoke script syntax is valid"

say "\nNebulaOps v23.4 extensions E2E smoke OK"
