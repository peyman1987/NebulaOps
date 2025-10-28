#!/usr/bin/env bash
# v22.1 — Reset Keycloak realm state and start the platform with OAuth2 Proxy protected tool UIs.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

PROJECT_NAME="${COMPOSE_PROJECT_NAME:-nebulaops-v22-1}"

echo "[nebulaops] Stopping stack and removing old Keycloak/OAuth2 session state..."
docker compose -p "$PROJECT_NAME" -f docker-compose.yml down --remove-orphans || true

echo "[nebulaops] Removing Keycloak DB volume so realm/theme/client redirects are reimported..."
for volume in \
  "${PROJECT_NAME}_keycloak-db-data" \
  "nebulaops-v22-1_keycloak-db-data" \
  "nebulaops_keycloak-db-data" \
  "keycloak-db-data"; do
  docker volume rm "$volume" 2>/dev/null || true
done

echo "[nebulaops] Starting Keycloak + platform with OAuth2 Proxy for Mongo/RabbitMQ/Redis..."
./scripts/docker-network-fix.sh >/dev/null 2>&1 || true
./scripts/wsl/start.sh --with-sso-proxy

echo "[nebulaops] Done. Open:"
echo "  Frontend   http://localhost:4200"
echo "  Grafana    http://localhost:3000"
echo "  Keycloak   http://localhost:8180"
echo "  RabbitMQ   http://localhost:15672   Keycloak SSO"
echo "  Mongo UI   http://localhost:8088    Keycloak SSO"
echo "  Redis UI   http://localhost:8089    Keycloak SSO"
