#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://nebulaops.localhost}"
TOKEN_HEADER=()
if [[ -n "${NEBULAOPS_TOKEN:-}" ]]; then
  TOKEN_HEADER=(-H "Authorization: Bearer ${NEBULAOPS_TOKEN}")
fi

echo "▶ Checking Operational Issues row-level actions at ${BASE_URL}"
issues_json="$(curl -fsS "${TOKEN_HEADER[@]}" "${BASE_URL}/api/platform/issues")"
ISSUES_JSON="${issues_json}" python3 - <<'PY'
import json, os
payload = json.loads(os.environ['ISSUES_JSON'])
if payload.get('realDataOnly') is not True:
    raise SystemExit('Operational issues response is not marked realDataOnly=true')
items = payload.get('items') or []
print(f"Operational issues returned: {len(items)}")
for item in items[:10]:
    actions = item.get('troubleshootingActions') or []
    if not actions:
        raise SystemExit(f"Issue has no troubleshootingActions: {item.get('id')}")
    if not item.get('actionsEndpoint'):
        raise SystemExit(f"Issue has no actionsEndpoint: {item.get('id')}")
print('Issue action catalog check OK')
PY

first_issue_id="$(ISSUES_JSON="${issues_json}" python3 - <<'PY'
import json, os
items = json.loads(os.environ['ISSUES_JSON']).get('items') or []
print(items[0].get('id','') if items else '')
PY
)"

if [[ -n "${first_issue_id}" ]]; then
  echo "▶ Checking action catalog endpoint for ${first_issue_id}"
  actions_json="$(curl -fsS "${TOKEN_HEADER[@]}" "${BASE_URL}/api/platform/issues/${first_issue_id}/actions")"
  ACTIONS_JSON="${actions_json}" python3 - <<'PY'
import json, os
payload = json.loads(os.environ['ACTIONS_JSON'])
if payload.get('ok') is not True:
    raise SystemExit('actions endpoint did not return ok=true')
items = payload.get('items') or []
if not items:
    raise SystemExit('actions endpoint returned no items')
print(f"Action endpoint returned {len(items)} actions")
PY
else
  echo "No current live issues found. Empty-state behavior is valid."
fi

echo "Operational Issues row-level actions smoke check OK"
