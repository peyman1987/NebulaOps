#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://nebulaops.localhost}"
TOKEN_HEADER=()
if [[ -n "${NEBULAOPS_TOKEN:-}" ]]; then
  TOKEN_HEADER=(-H "Authorization: Bearer ${NEBULAOPS_TOKEN}")
fi

echo "▶ Checking Safe Action Plan endpoints at ${BASE_URL}"

docker_payload='{"domain":"docker","action":"system.prune","target":"docker","parameters":{"volumes":false}}'
docker_plan="$(curl -fsS "${TOKEN_HEADER[@]}" -H 'Content-Type: application/json' -d "${docker_payload}" "${BASE_URL}/api/runtime/docker/actions/plan")"
DOCKER_PLAN="${docker_plan}" python3 - <<'PY'
import json, os
payload = json.loads(os.environ['DOCKER_PLAN'])
if payload.get('ok') is True:
    plan = payload.get('plan') or {}
    required = ['whatWillBeTouched','impactedResources','dependencies','reversible','reversibility','command','apiCall','risk','confirmationPhrase','executionEndpoint','executePayload','planId','realDataOnly']
    missing = [key for key in required if key not in plan]
    if missing:
        raise SystemExit('Docker safe-action plan missing fields: ' + ', '.join(missing))
    if plan.get('realDataOnly') is not True:
        raise SystemExit('Docker safe-action plan is not marked realDataOnly=true')
    print('Docker Safe Action Plan OK')
else:
    state = payload.get('state') or payload.get('error') or 'UNKNOWN'
    if state not in {'DOCKER_UNAVAILABLE','ACTION_REQUIRED'}:
        raise SystemExit(f'Docker plan failed unexpectedly: {state}')
    print(f'Docker plan endpoint reachable, runtime state: {state}')
PY

k8s_payload='{"domain":"kubernetes","action":"yaml.apply","target":"manifest","parameters":{"yaml":"apiVersion: v1\nkind: Namespace\nmetadata:\n  name: nebulaops-safe-plan-smoke"}}'
k8s_plan="$(curl -fsS "${TOKEN_HEADER[@]}" -H 'Content-Type: application/json' -d "${k8s_payload}" "${BASE_URL}/api/kubernetes/actions/plan")"
K8S_PLAN="${k8s_plan}" python3 - <<'PY'
import json, os
payload = json.loads(os.environ['K8S_PLAN'])
if payload.get('ok') is not True:
    raise SystemExit('Kubernetes safe-action plan endpoint did not return ok=true')
plan = payload.get('plan') or {}
required = ['whatWillBeTouched','impactedResources','dependencies','reversible','reversibility','command','apiCall','risk','confirmationPhrase','executionEndpoint','executePayload','planId','realDataOnly']
missing = [key for key in required if key not in plan]
if missing:
    raise SystemExit('Kubernetes safe-action plan missing fields: ' + ', '.join(missing))
if plan.get('realDataOnly') is not True:
    raise SystemExit('Kubernetes safe-action plan is not marked realDataOnly=true')
print('Kubernetes Safe Action Plan OK')
PY

blocked_payload="$(K8S_PLAN="${k8s_plan}" python3 - <<'PY'
import json, os
payload = json.loads(os.environ['K8S_PLAN'])
plan = payload.get('plan') or {}
print(json.dumps({'plan': plan, 'confirmation': 'WRONG-CONFIRMATION'}))
PY
)"
blocked="$(curl -fsS "${TOKEN_HEADER[@]}" -H 'Content-Type: application/json' -d "${blocked_payload}" "${BASE_URL}/api/platform/actions/execute")"
BLOCKED="${blocked}" python3 - <<'PY'
import json, os
payload = json.loads(os.environ['BLOCKED'])
if payload.get('ok') is not False or payload.get('state') != 'CONFIRMATION_REQUIRED':
    raise SystemExit('Safe action execute did not block a wrong confirmation phrase')
print('Execution gate blocks wrong confirmation phrases OK')
PY

echo "Safe Action Plan smoke check OK"
