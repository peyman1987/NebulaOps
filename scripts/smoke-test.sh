#!/usr/bin/env bash
# v23.3 — Authenticated smoke test through gateway using Keycloak token.
set -euo pipefail
BASE=${BASE:-http://nebulaops.localhost}
KEYCLOAK_URL=${KEYCLOAK_URL:-http://nebulaops.localhost/keycloak}
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

echo "Creating task through gateway..."
TASK_RESPONSE=$(curl -fsS "${AUTH[@]}" -H 'Content-Type: application/json' \
  -d '{"organizationId":"nebulaops","projectId":"runtime-validation","title":"Smoke test RabbitMQ event","description":"Created by smoke-test.sh","priority":"HIGH","labels":["smoke","rabbitmq","mongodb","keycloak"]}' \
  "$BASE/api/tasks")
echo "$TASK_RESPONSE"

echo "Listing tasks..."
curl -fsS "${AUTH[@]}" "$BASE/api/tasks?organizationId=nebulaops"

echo
echo "Checking Go cache service"
curl -fsS http://localhost:8091/health >/dev/null && echo "go-cache-service OK"

echo "Smoke test completed."
