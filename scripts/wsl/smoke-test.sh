#!/usr/bin/env bash
# v22.1 — Authenticated smoke test through gateway using Keycloak token.
set -euo pipefail
BASE=${BASE:-http://localhost:8080}
KEYCLOAK_URL=${KEYCLOAK_URL:-http://localhost:8180}
SMOKE_USER=${NEBULAOPS_SMOKE_USER:-admin}
SMOKE_PASSWORD=${NEBULAOPS_SMOKE_PASSWORD:-admin}

get_token() {
  curl -fsS -X POST "$KEYCLOAK_URL/realms/nebulaops/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "username=$SMOKE_USER" \
    --data-urlencode "password=$SMOKE_PASSWORD" \
    --data-urlencode 'grant_type=password' \
    --data-urlencode 'client_id=nebulaops-frontend' \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])'
}

TOKEN="$(get_token)"
AUTH=(-H "Authorization: Bearer $TOKEN")

echo "Checking gateway health..."
curl -fsS "$BASE/actuator/health" >/dev/null

echo "Creating task through gateway with Keycloak token..."
curl -fsS "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d '{"organizationId":"demo-org","projectId":"portfolio","title":"WSL smoke test task","description":"Created from scripts/wsl/smoke-test.sh","priority":"HIGH","labels":["wsl","rabbitmq","mongodb","keycloak"]}' \
  "$BASE/api/tasks"

echo
echo "Tasks:"
curl -fsS "${AUTH[@]}" "$BASE/api/tasks?organizationId=demo-org"

echo
echo "Notifications:"
curl -fsS "${AUTH[@]}" "$BASE/api/notifications" || true

echo
echo "Checking Go cache service"
curl -fsS http://localhost:8091/health >/dev/null && echo "go-cache-service OK"

echo "Smoke test completed."
