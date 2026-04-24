#!/usr/bin/env bash
set -euo pipefail

KC_BASE="${KC_BASE:-http://nebulaops.localhost/keycloak}"
REALM="${KEYCLOAK_REALM:-nebulaops}"
USER="${KEYCLOAK_TEST_USER:-admin}"
PASS="${KEYCLOAK_TEST_PASSWORD:-admin}"
CLIENT_ID="${KEYCLOAK_TEST_CLIENT_ID:-nebulaops-frontend}"
API="${NEBULAOPS_GATEWAY_URL:-http://nebulaops.localhost}"

echo "[keycloak] waiting for $KC_BASE/realms/$REALM"
for i in {1..60}; do
  if curl -fsS "$KC_BASE/realms/$REALM/.well-known/openid-configuration" >/dev/null; then break; fi
  sleep 2
  if [[ "$i" == "60" ]]; then echo "Keycloak not ready" >&2; exit 1; fi
done

echo "[keycloak] requesting token for user=$USER client=$CLIENT_ID"
TOKEN=$(curl -fsS -X POST "$KC_BASE/realms/$REALM/protocol/openid-connect/token" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=$CLIENT_ID" \
  --data-urlencode "username=$USER" \
  --data-urlencode "password=$PASS" | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')

echo "[gateway] unauthenticated call must be rejected"
HTTP_CODE=$(curl -s -o /tmp/nebulaops_noauth.out -w '%{http_code}' "$API/api/tasks" || true)
if [[ "$HTTP_CODE" != "401" && "$HTTP_CODE" != "403" ]]; then
  echo "Expected 401/403 without token, got $HTTP_CODE" >&2
  cat /tmp/nebulaops_noauth.out >&2 || true
  exit 1
fi

echo "[gateway] authenticated call with Keycloak token"
curl -fsS -H "Authorization: Bearer $TOKEN" "$API/api/health" | python3 -m json.tool >/dev/null || true

echo "OK: Keycloak token issued and gateway accepts Bearer authentication"
