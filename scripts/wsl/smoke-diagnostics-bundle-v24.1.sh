#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${NEBULAOPS_BASE_URL:-${BASE_URL:-http://nebulaops.localhost}}"
AUTH_HEADER=()
if [[ -n "${NEBULAOPS_AUTH_TOKEN:-${NEBULAOPS_TOKEN:-}}" ]]; then
  AUTH_HEADER=(-H "Authorization: Bearer ${NEBULAOPS_AUTH_TOKEN:-${NEBULAOPS_TOKEN:-}}")
fi

echo "▶ NebulaOps v24.1 diagnostics bundle smoke"
echo "  baseUrl=${BASE_URL}"

manifest_file="$(mktemp)"
zip_file="$(mktemp --suffix=.zip)"
trap 'rm -f "$manifest_file" "$zip_file"' EXIT

curl -fsS "${AUTH_HEADER[@]}" "${BASE_URL}/api/runtime/diagnostics/bundle" -o "$manifest_file"
python3 - "$manifest_file" <<'PY'
import json, sys
with open(sys.argv[1], 'r', encoding='utf-8') as f:
    payload = json.load(f)
if payload.get('version') != '24.1.0':
    raise SystemExit('unexpected bundle version')
if payload.get('realDataOnly') is not True:
    raise SystemExit('bundle is not marked realDataOnly=true')
required = {
    'docker/containers.json',
    'docker/images.json',
    'docker/networks.json',
    'docker/events.json',
    'docker/compose-projects.json',
    'kubernetes/pods.json',
    'kubernetes/events.json',
    'kubernetes/describe/pods.txt',
    'helm/releases.json',
    'gateway/health.json',
    'extensions/status.json',
    'frontend/remote-verification.txt',
    'preflight/preflight.txt',
}
paths = {entry.get('path') for entry in payload.get('entries', [])}
missing = sorted(required - paths)
if missing:
    raise SystemExit('manifest missing entries: ' + ', '.join(missing))
print('✓ manifest contains the required live diagnostic sections')
PY

curl -fsS "${AUTH_HEADER[@]}" "${BASE_URL}/api/runtime/diagnostics/bundle.zip" -o "$zip_file"
python3 - "$zip_file" <<'PY'
import sys, zipfile
required = {
    'manifest.json',
    'docker/containers.json',
    'docker/images.json',
    'docker/networks.json',
    'docker/events.json',
    'docker/compose-projects.json',
    'kubernetes/pods.json',
    'kubernetes/events.json',
    'kubernetes/describe/pods.txt',
    'helm/releases.json',
    'gateway/health.json',
    'extensions/status.json',
    'frontend/remote-verification.txt',
    'preflight/preflight.txt',
}
with zipfile.ZipFile(sys.argv[1]) as zf:
    names = set(zf.namelist())
    missing = sorted(required - names)
    if missing:
        raise SystemExit('zip missing entries: ' + ', '.join(missing))
    manifest = zf.read('manifest.json').decode('utf-8')
    if 'realDataOnly' not in manifest:
        raise SystemExit('zip manifest missing realDataOnly marker')
print('✓ downloadable ZIP contains the required live diagnostic files')
PY

echo "Diagnostics bundle smoke check OK"
