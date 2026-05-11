#!/usr/bin/env bash
# Shared helpers for NebulaOps v23.4 runtime E2E smoke tests.
# The helpers intentionally validate live API contracts instead of accepting
# fallback HTML/error pages or bundled offline records.

set -euo pipefail

NEBULAOPS_BASE_URL="${NEBULAOPS_BASE_URL:-${BASE_URL:-http://nebulaops.localhost}}"
NEBULAOPS_CURL_TIMEOUT="${NEBULAOPS_CURL_TIMEOUT:-20}"
NEBULAOPS_EXTENSION_SMOKE_SLUG="${NEBULAOPS_EXTENSION_SMOKE_SLUG:-apiforge}"

AUTH_HEADER=()
if [[ -n "${NEBULAOPS_AUTH_TOKEN:-${NEBULAOPS_TOKEN:-}}" ]]; then
  AUTH_HEADER=(-H "Authorization: Bearer ${NEBULAOPS_AUTH_TOKEN:-${NEBULAOPS_TOKEN:-}}")
fi

say() { printf '%s\n' "$*"; }
ok() { printf '✓ %s\n' "$*"; }
warn() { printf '⚠ %s\n' "$*"; }
fail() { printf '✗ %s\n' "$*" >&2; exit 1; }

mkbody() { mktemp "${TMPDIR:-/tmp}/nebulaops-smoke.XXXXXX"; }

http_request() {
  local method="$1" path="$2" body="${3:-}" timeout="${4:-$NEBULAOPS_CURL_TIMEOUT}"
  local url
  if [[ "$path" =~ ^https?:// ]]; then
    url="$path"
  else
    url="${NEBULAOPS_BASE_URL}${path}"
  fi
  local out meta
  out="$(mkbody)"
  if [[ -n "$body" ]]; then
    meta="$(curl -sS --max-time "$timeout" -o "$out" -w '%{http_code}|%{content_type}|%{time_total}' -X "$method" "${AUTH_HEADER[@]}" -H 'Accept: application/json' -H 'Content-Type: application/json' --data "$body" "$url" || true)"
  else
    meta="$(curl -sS --max-time "$timeout" -o "$out" -w '%{http_code}|%{content_type}|%{time_total}' -X "$method" "${AUTH_HEADER[@]}" -H 'Accept: application/json' "$url" || true)"
  fi
  printf '%s|%s\n' "$meta" "$out"
}

assert_json_endpoint() {
  local method="$1" path="$2" label="$3" body="${4:-}" max_seconds="${5:-$NEBULAOPS_CURL_TIMEOUT}"
  local meta code content_type elapsed file
  IFS='|' read -r code content_type elapsed file < <(http_request "$method" "$path" "$body" "$max_seconds")

  if [[ ! "$code" =~ ^[0-9]{3}$ ]]; then
    cat "$file" >&2 || true
    rm -f "$file"
    fail "$label did not return a valid HTTP status"
  fi
  if [[ "$code" == "504" ]]; then
    cat "$file" >&2 || true
    rm -f "$file"
    fail "$label returned HTTP 504 Gateway Timeout"
  fi
  if (( code < 200 || code >= 500 )); then
    cat "$file" >&2 || true
    rm -f "$file"
    fail "$label returned HTTP $code"
  fi

  python3 - "$file" "$label" "$content_type" <<'PY'
import json
import pathlib
import sys
path, label, content_type = sys.argv[1], sys.argv[2], sys.argv[3]
raw = pathlib.Path(path).read_text(encoding='utf-8', errors='replace').strip()
if not raw:
    raise SystemExit(f'{label} returned an empty body')
if raw.startswith('<!doctype') or raw.startswith('<html') or '<html' in raw[:300].lower():
    raise SystemExit(f'{label} returned HTML instead of JSON')
try:
    payload = json.loads(raw)
except Exception as exc:
    raise SystemExit(f'{label} returned non-JSON body: {exc}')
if not isinstance(payload, (dict, list)):
    raise SystemExit(f'{label} returned JSON but not an object/list')
if isinstance(payload, dict):
    # If a live source is unavailable, the contract must say so explicitly.
    live = payload.get('live')
    if live is False:
        explicit = any(k in payload for k in ('state', 'status', 'error', 'message', 'toolStatus', 'mode'))
        if not explicit:
            raise SystemExit(f'{label} returned live=false without explicit state/status/error/toolStatus')
    # Avoid server/framework error pages serialized as JSON without useful state.
    if 'timestamp' in payload and 'path' in payload and 'error' in payload and 'status' in payload:
        raise SystemExit(f'{label} returned a framework error JSON: {payload.get("status")} {payload.get("error")}')
print('OK')
PY
  rm -f "$file"
  ok "$label returned JSON in ${elapsed}s"
}

assert_status_under() {
  local method="$1" path="$2" label="$3" limit_seconds="$4" expected_code="${5:-}"
  local meta code content_type elapsed file
  IFS='|' read -r code content_type elapsed file < <(http_request "$method" "$path" "" "$limit_seconds")
  rm -f "$file"
  [[ "$code" =~ ^[0-9]{3}$ ]] || fail "$label did not return an HTTP status"
  [[ "$code" != "504" ]] || fail "$label returned HTTP 504"
  if [[ -n "$expected_code" && "$code" != "$expected_code" ]]; then
    fail "$label returned HTTP $code, expected $expected_code"
  fi
  python3 - "$elapsed" "$limit_seconds" "$label" <<'PY'
import sys
elapsed=float(sys.argv[1] or 999)
limit=float(sys.argv[2])
label=sys.argv[3]
if elapsed > limit:
    raise SystemExit(f'{label} took {elapsed:.2f}s, limit is {limit:.2f}s')
print('OK')
PY
  ok "$label returned HTTP $code in ${elapsed}s"
}

assert_json_field() {
  local file="$1" field="$2"
  python3 - "$file" "$field" <<'PY'
import json, sys
path, field = sys.argv[1], sys.argv[2]
with open(path, 'r', encoding='utf-8') as fh:
    cur = json.load(fh)
for part in field.split('.'):
    if isinstance(cur, dict) and part in cur:
        cur = cur[part]
    else:
        raise SystemExit(f'missing JSON field: {field}')
print(cur)
PY
}

assert_json_body_semantics() {
  local path="$1" label="$2"
  local meta code content_type elapsed file
  IFS='|' read -r code content_type elapsed file < <(http_request GET "$path")
  if (( code < 200 || code >= 500 )) || [[ "$code" == "504" ]]; then
    cat "$file" >&2 || true
    rm -f "$file"
    fail "$label returned HTTP $code"
  fi
  python3 - "$file" "$label" <<'PY'
import json, pathlib, sys
raw=pathlib.Path(sys.argv[1]).read_text(encoding='utf-8', errors='replace').strip()
label=sys.argv[2]
if raw.startswith('<'):
    raise SystemExit(f'{label} returned HTML')
payload=json.loads(raw)
# Lists are allowed only as real runtime collections. Empty lists are accepted
# when the companion status endpoint is checked separately by the caller.
if isinstance(payload, dict) and payload.get('live') is False:
    text=json.dumps(payload).upper()
    if not any(marker in text for marker in ('UNAVAILABLE','NOT_CONFIGURED','DEGRADED','TIMEOUT','ERROR')):
        raise SystemExit(f'{label} has live=false but no explicit unavailable/degraded state')
print('OK')
PY
  rm -f "$file"
  ok "$label contract is explicit"
}

require_tool() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}
