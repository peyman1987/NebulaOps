#!/usr/bin/env bash
# v23.4 — Focused diagnostics for Grafana/RabbitMQ/Mongo/Redis Keycloak SSO.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

echo "== Compose SSO containers =="
dc ps --format "table {{.Service}}\t{{.Status}}\t{{.Ports}}" | grep -E 'keycloak|grafana|rabbitmq|mongo-express|redis-commander|sso|basic-proxy' || true

echo
echo "== Ensuring Keycloak clients =="
./scripts/keycloak-ensure-sso-clients.sh || true

echo
echo "== Public SSO redirect probes =="
for pair in \
  "RabbitMQ http://localhost:${RABBITMQ_SSO_HOST_PORT:-15673}/" \
  "Mongo http://localhost:${MONGO_EXPRESS_SSO_HOST_PORT:-18088}/" \
  "Redis http://localhost:${REDIS_COMMANDER_SSO_HOST_PORT:-18089}/" \
  "Grafana http://localhost:${GRAFANA_HOST_PORT:-3300}/login/generic_oauth"; do
  label="${pair%% *}"
  url="${pair#* }"
  tmp="$(mktemp)"
  code=$(curl -sS --max-time 8 -o /dev/null -D "$tmp" -w '%{http_code}' "$url" 2>/dev/null || true)
  location=$(awk 'BEGIN{IGNORECASE=1} /^location:/ {sub(/^[Ll]ocation:[[:space:]]*/, ""); gsub(/\r/, ""); print; exit}' "$tmp" 2>/dev/null || true)
  rm -f "$tmp"
  printf '%-10s HTTP %-4s %s\n' "$label" "${code:-000}" "${location:-}"
done

echo
echo "== Internal SSO bridge upstream checks =="
for pair in \
  "rabbitmq-basic-proxy RabbitMQ" \
  "mongo-express-basic-proxy Mongo" \
  "redis-commander-basic-proxy Redis"; do
  svc="${pair%% *}"
  label="${pair#* }"
  if dc ps --services --filter status=running | grep -qx "$svc"; then
    self=$(dc exec -T "$svc" sh -c "wget -q -O - http://127.0.0.1:8080/__nebulaops_proxy_health 2>/dev/null" 2>/dev/null || true)
    code=$(dc exec -T "$svc" sh -c "wget -q -S -O /dev/null http://127.0.0.1:8080/ 2>&1 | awk '/HTTP\\// {c=\\$2} END {print c}'" 2>/dev/null || true)
    printf '%-10s bridge self=%s upstream HTTP %s\n' "$label" "${self:-NO}" "${code:-000}"
  else
    printf '%-10s bridge not running (%s)\n' "$label" "$svc"
  fi
done

echo
echo "== DNS from SSO bridge containers =="
for svc in rabbitmq-basic-proxy mongo-express-basic-proxy redis-commander-basic-proxy; do
  if dc ps --services --filter status=running | grep -qx "$svc"; then
    echo "--- $svc ---"
    dc exec -T "$svc" sh -c 'getent hosts rabbitmq mongo-express redis-commander 2>/dev/null || true' || true
  fi
done

echo
echo "== Recent SSO and bridge logs =="
for svc in rabbitmq-management-sso mongo-express-sso redis-commander-sso rabbitmq-basic-proxy mongo-express-basic-proxy redis-commander-basic-proxy; do
  if dc ps --services --filter status=running | grep -qx "$svc"; then
    echo "--- $svc ---"
    dc logs "$svc" --tail=80 || true
  fi
done
