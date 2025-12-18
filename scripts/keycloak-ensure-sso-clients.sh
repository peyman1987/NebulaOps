#!/usr/bin/env bash
# v22.3 — Ensure NebulaOps Keycloak realm and all OIDC clients exist even when an old Keycloak DB volume is reused.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REALM_FILE="$ROOT_DIR/infrastructure/keycloak/realm-nebulaops.json"
KEYCLOAK_PUBLIC_URL="${KEYCLOAK_PUBLIC_URL:-http://nebulaops.localhost/keycloak}"
KEYCLOAK_ADMIN_URL="${KEYCLOAK_ADMIN_URL:-${KEYCLOAK_DIRECT_URL:-http://localhost:8180}}"
REALM="${KEYCLOAK_REALM:-nebulaops}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REQUIRED_CLIENTS=(
  nebulaops-frontend
  nebulaops-gateway
  nebulaops-api
  grafana
  nebulaops-oauth2-proxy
  gitlab
)

wait_keycloak() {
  for _ in $(seq 1 180); do
    if curl -fsS "$KEYCLOAK_PUBLIC_URL/health/ready" >/dev/null 2>&1 || \
       curl -fsS "$KEYCLOAK_ADMIN_URL/health/ready" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "[keycloak] Keycloak not ready: $KEYCLOAK_PUBLIC_URL/health/ready or $KEYCLOAK_ADMIN_URL/health/ready" >&2
  return 1
}

admin_token() {
  local response token
  response=$(curl -fsS --retry 12 --retry-delay 2 --retry-all-errors \
    -X POST "$KEYCLOAK_ADMIN_URL/realms/master/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "username=$ADMIN_USER" \
    --data-urlencode "password=$ADMIN_PASSWORD" \
    --data-urlencode 'grant_type=password' \
    --data-urlencode 'client_id=admin-cli' 2>/dev/null || true)
  token=$(printf '%s' "$response" | python3 -c 'import json,sys; data=sys.stdin.read(); print(json.loads(data).get("access_token", "") if data.strip() else "")' 2>/dev/null || true)
  if [ -n "$token" ]; then
    printf '%s\n' "$token"
    return 0
  fi
  echo "[keycloak] cannot obtain admin token from $KEYCLOAK_ADMIN_URL. Response was: ${response:-<empty>}" >&2
  return 1
}

realm_exists() {
  local bearer="$1"
  curl -fsS "$KEYCLOAK_ADMIN_URL/admin/realms/$REALM" \
    -H "Authorization: Bearer $bearer" >/dev/null 2>&1
}

ensure_realm() {
  local bearer="$1"
  if realm_exists "$bearer"; then
    echo "[keycloak] realm exists: $REALM"
    return 0
  fi
  echo "[keycloak] creating realm from $REALM_FILE"
  curl -fsS -X POST "$KEYCLOAK_ADMIN_URL/admin/realms" \
    -H "Authorization: Bearer $bearer" \
    -H 'Content-Type: application/json' \
    --data-binary "@$REALM_FILE" >/dev/null
}

client_uuid() {
  local bearer="$1" client_id="$2"
  curl -fsS "$KEYCLOAK_ADMIN_URL/admin/realms/$REALM/clients?clientId=$client_id" \
    -H "Authorization: Bearer $bearer" \
  | python3 -c 'import json,sys; data=json.load(sys.stdin); print(data[0]["id"] if data else "")'
}

client_payload() {
  local client_id="$1"
  python3 - "$REALM_FILE" "$client_id" <<'PY'
import copy, json, sys
realm_file, client_id = sys.argv[1:]
realm = json.load(open(realm_file, encoding='utf-8'))
for client in realm.get('clients', []):
    if client.get('clientId') == client_id:
        body = copy.deepcopy(client)
        # Keep admin-export fields out of client create/update payloads if they appear later.
        for key in ('id', 'internalId'):
            body.pop(key, None)
        print(json.dumps(body))
        raise SystemExit(0)
raise SystemExit(f'client not found in realm file: {client_id}')
PY
}

ensure_client() {
  local bearer="$1" client_id="$2" uuid body
  body="$(client_payload "$client_id")"
  uuid="$(client_uuid "$bearer" "$client_id")"
  if [ -z "$uuid" ]; then
    echo "[keycloak] creating client: $client_id"
    curl -fsS -X POST "$KEYCLOAK_ADMIN_URL/admin/realms/$REALM/clients" \
      -H "Authorization: Bearer $bearer" \
      -H 'Content-Type: application/json' \
      -d "$body" >/dev/null
  else
    echo "[keycloak] updating client: $client_id"
    curl -fsS -X PUT "$KEYCLOAK_ADMIN_URL/admin/realms/$REALM/clients/$uuid" \
      -H "Authorization: Bearer $bearer" \
      -H 'Content-Type: application/json' \
      -d "$body" >/dev/null
  fi
}

set_realm_theme() {
  local bearer="$1"
  curl -fsS -X PUT "$KEYCLOAK_ADMIN_URL/admin/realms/$REALM" \
    -H "Authorization: Bearer $bearer" \
    -H 'Content-Type: application/json' \
    -d '{"loginTheme":"nebulaops","accountTheme":"keycloak","adminTheme":"keycloak","emailTheme":"keycloak"}' >/dev/null
}

verify_client() {
  local bearer="$1" client_id="$2" uuid
  uuid="$(client_uuid "$bearer" "$client_id")"
  [ -n "$uuid" ] || { echo "[keycloak] missing client after ensure: $client_id" >&2; return 1; }
}

wait_keycloak
BEARER="$(admin_token)"
ensure_realm "$BEARER"
set_realm_theme "$BEARER" || true

for client_id in "${REQUIRED_CLIENTS[@]}"; do
  ensure_client "$BEARER" "$client_id"
done

for client_id in "${REQUIRED_CLIENTS[@]}"; do
  verify_client "$BEARER" "$client_id"
done

echo "OK Keycloak realm and clients ready: ${REQUIRED_CLIENTS[*]}"
