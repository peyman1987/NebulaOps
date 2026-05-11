#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${NEBULAOPS_BASE_URL:-http://nebulaops.localhost}"
EXTENSION_SLUG="${NEBULAOPS_EXTENSION_SMOKE_SLUG:-apiforge}"
AUTH_HEADER=()
if [[ -n "${NEBULAOPS_AUTH_TOKEN:-}" ]]; then
  AUTH_HEADER=(-H "Authorization: Bearer ${NEBULAOPS_AUTH_TOKEN}")
fi

echo "▶ NebulaOps v23.4 async extension control-plane smoke"
echo "  baseUrl=${BASE_URL}"
echo "  extension=${EXTENSION_SLUG}"

fetch_json() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  local tmp_body tmp_code
  tmp_body="$(mktemp)"
  if [[ -n "$body" ]]; then
    tmp_code="$(curl -sS -o "$tmp_body" -w '%{http_code}' -X "$method" "${AUTH_HEADER[@]}" -H 'Content-Type: application/json' --data "$body" "$url")"
  else
    tmp_code="$(curl -sS -o "$tmp_body" -w '%{http_code}' -X "$method" "${AUTH_HEADER[@]}" "$url")"
  fi
  echo "$tmp_code $tmp_body"
}

assert_json_field() {
  local file="$1"
  local field="$2"
  python3 - "$file" "$field" <<'PY'
import json, sys
path, field = sys.argv[1], sys.argv[2]
with open(path, 'r', encoding='utf-8') as f:
    data = json.load(f)
cur = data
for part in field.split('.'):
    if isinstance(cur, dict) and part in cur:
        cur = cur[part]
    else:
        raise SystemExit(f"missing JSON field: {field}")
print(cur)
PY
}

read code body < <(fetch_json GET "${BASE_URL}/api/extensions")
if [[ "$code" != "200" ]]; then
  echo "✗ /api/extensions returned HTTP ${code}"
  cat "$body"
  exit 1
fi
assert_json_field "$body" realDataOnly >/dev/null
assert_json_field "$body" mode >/dev/null
echo "✓ /api/extensions registry is reachable"
rm -f "$body"

read code body < <(fetch_json GET "${BASE_URL}/api/extensions/${EXTENSION_SLUG}/events")
if [[ "$code" != "200" ]]; then
  echo "✗ /api/extensions/${EXTENSION_SLUG}/events returned HTTP ${code}"
  cat "$body"
  exit 1
fi
assert_json_field "$body" realDataOnly >/dev/null
assert_json_field "$body" items >/dev/null
echo "✓ /api/extensions/${EXTENSION_SLUG}/events is reachable"
rm -f "$body"

read code body < <(fetch_json GET "${BASE_URL}/api/extensions/${EXTENSION_SLUG}/diagnostics")
if [[ "$code" != "200" ]]; then
  echo "✗ /api/extensions/${EXTENSION_SLUG}/diagnostics returned HTTP ${code}"
  cat "$body"
  exit 1
fi
assert_json_field "$body" recentEvents >/dev/null
assert_json_field "$body" recentOperations >/dev/null
echo "✓ diagnostics include async events and operations"
rm -f "$body"

if [[ "${NEBULAOPS_RUN_ASYNC_EXTENSION_START_SMOKE:-0}" != "1" ]]; then
  echo "ℹ Skipping POST /api/extensions/${EXTENSION_SLUG}/start because it deploys a real extension."
  echo "  To verify the full 202 Accepted flow, rerun with NEBULAOPS_RUN_ASYNC_EXTENSION_START_SMOKE=1."
  exit 0
fi

read code body < <(fetch_json POST "${BASE_URL}/api/extensions/${EXTENSION_SLUG}/start" '{}')
if [[ "$code" != "202" ]]; then
  echo "✗ start returned HTTP ${code}; expected 202"
  cat "$body"
  exit 1
fi
operation_id="$(assert_json_field "$body" operationId)"
operation_url="$(assert_json_field "$body" operationUrl)"
echo "✓ start accepted with operationId=${operation_id}"
rm -f "$body"

for _ in $(seq 1 40); do
  read code body < <(fetch_json GET "${BASE_URL}${operation_url}")
  if [[ "$code" != "200" ]]; then
    echo "✗ operation status returned HTTP ${code}"
    cat "$body"
    exit 1
  fi
  phase="$(assert_json_field "$body" phase)"
  state="$(assert_json_field "$body" state)"
  echo "  operation phase=${phase} state=${state}"
  if [[ "$state" =~ ^(SUCCEEDED|FAILED|TIMEOUT)$ || "$phase" =~ ^(READY|FAILED|TIMEOUT)$ ]]; then
    cat "$body"
    rm -f "$body"
    exit 0
  fi
  rm -f "$body"
  sleep 3
done

echo "✗ operation did not reach a terminal state within the smoke timeout"
exit 1
