#!/usr/bin/env bash
# Apply NebulaOps theme to the nebulaops realm via Keycloak Admin REST API
# Run this after Keycloak is up: ./scripts/wsl/keycloak-apply-theme.sh

set -euo pipefail
KC_URL="${KC_URL:-http://nebulaops.localhost/keycloak}"
KC_ADMIN="${KC_ADMIN:-admin}"
KC_ADMIN_PASS="${KC_ADMIN_PASS:-admin}"
REALM="${KC_REALM:-nebulaops}"

echo "▶ Applying NebulaOps theme to Keycloak realm '$REALM'..."

# Get admin token
TOKEN=$(curl -sf -X POST "${KC_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli&username=${KC_ADMIN}&password=${KC_ADMIN_PASS}&grant_type=password" \
  | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "✗ Failed to get admin token — is Keycloak running on ${KC_URL}?"
  exit 1
fi
echo "✓ Admin token obtained"

# Apply theme to realm
curl -sf -X PUT "${KC_URL}/admin/realms/${REALM}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"loginTheme":"nebulaops","accountTheme":"keycloak","adminTheme":"keycloak","emailTheme":"keycloak"}' \
  && echo "✓ Theme 'nebulaops' applied to realm '${REALM}'" \
  || echo "✗ Failed to apply theme"

echo ""
echo "  Open http://nebulaops.localhost/keycloak/realms/${REALM}/account to verify the login page."
