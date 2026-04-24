#!/usr/bin/env bash
# v23.2 — Reimport the NebulaOps Keycloak realm by removing the Keycloak DB volume.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
PROJECT_NAME="${COMPOSE_PROJECT_NAME:-nebulaops-v23-2}"
WITH_SSO_PROXY=false
for arg in "$@"; do
  case "$arg" in
    --with-sso-proxy|--with-sso-proxies) WITH_SSO_PROXY=true ;;
  esac
done

echo "[keycloak] stopping stack"
docker compose -p "$PROJECT_NAME" -f docker-compose.yml down --remove-orphans || true

echo "[keycloak] removing Keycloak DB volume so realm-nebulaops.json is imported again"
for volume in \
  "${PROJECT_NAME}_keycloak-db-data" \
  "nebulaops-v23-2_keycloak-db-data" \
  "nebulaops_keycloak-db-data" \
  "keycloak-db-data"; do
  docker volume rm "$volume" 2>/dev/null || true
done

if [ "$WITH_SSO_PROXY" = "true" ]; then
  echo "[keycloak] starting platform with OAuth2 Proxy protected tool UIs"
  ./scripts/wsl/start.sh --with-sso-proxy
else
  echo "[keycloak] starting platform"
  ./scripts/wsl/start.sh
fi

echo "OK: Keycloak realm imported from infrastructure/keycloak/realm-nebulaops.json"
