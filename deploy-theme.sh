#!/usr/bin/env bash
# NebulaOps v22.5 — Deploy fixed Keycloak theme
# Run from the nebulaops-v22.5 project root:
#   bash path/to/deploy-theme.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(pwd)}"
DEST="$PROJECT_ROOT/infrastructure/keycloak"

echo "▶ Copying fixed theme files to $DEST ..."

cp "$SCRIPT_DIR/infrastructure/keycloak/themes/nebulaops/login/login.ftl" \
   "$DEST/themes/nebulaops/login/login.ftl"

cp "$SCRIPT_DIR/infrastructure/keycloak/themes/nebulaops/login/theme.properties" \
   "$DEST/themes/nebulaops/login/theme.properties"

cp "$SCRIPT_DIR/infrastructure/keycloak/themes/nebulaops/login/resources/css/nebulaops.css" \
   "$DEST/themes/nebulaops/login/resources/css/nebulaops.css"

echo "✓ Theme files copied"
echo ""
echo "▶ Restarting Keycloak container ..."
docker compose -p nebulaops-v22-5 restart keycloak

echo ""
echo "▶ Waiting for Keycloak to be ready (up to 60s) ..."
for i in $(seq 1 12); do
  STATUS=$(curl -sf http://localhost:8180/health/ready 2>/dev/null | grep -o '"status":"[^"]*"' | head -1 || true)
  if echo "$STATUS" | grep -q "UP"; then
    echo "✓ Keycloak is UP"
    break
  fi
  echo "  waiting... ($((i*5))s)"
  sleep 5
done

echo ""
echo "▶ Checking for FreeMarker errors ..."
ERRORS=$(docker compose -p nebulaops-v22-5 logs keycloak --tail=80 2>/dev/null | grep -iE 'freemarker|ParseException' || true)
if [ -z "$ERRORS" ]; then
  echo "✓ No FreeMarker errors — theme loaded successfully"
  echo ""
  echo "  Open: http://localhost:8180/realms/nebulaops/protocol/openid-connect/auth"
else
  echo "✗ FreeMarker errors detected:"
  echo "$ERRORS"
fi
