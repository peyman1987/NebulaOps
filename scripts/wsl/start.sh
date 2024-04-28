#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
PROJECT_NAME="nebulaops-v15"
./scripts/wsl/check-wsl.sh
./scripts/wsl/prepare-kubeconfig-for-docker.sh
echo "
# Guard: Grafana allows only one default datasource per organization.
DEFAULT_COUNT=$(grep -R "isDefault:[[:space:]]*true" -n infrastructure/observability/grafana/provisioning/datasources 2>/dev/null | wc -l | tr -d ' ')
if [ "${DEFAULT_COUNT:-0}" -gt 1 ]; then
  echo "FAIL Grafana provisioning has multiple default datasources. Keep only one isDefault: true."
  grep -R "isDefault:[[:space:]]*true" -n infrastructure/observability/grafana/provisioning/datasources || true
  exit 1
fi

Starting NebulaOps with Docker Compose..."
docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" up --build -d
echo "Waiting for gateway..."
for i in {1..60}; do
  if curl -fsS http://localhost:8080/actuator/health >/dev/null 2>&1; then echo "Gateway is healthy."; break; fi
  sleep 3
  [[ "$i" == "60" ]] && echo "Gateway not ready yet. Check: ./scripts/wsl/logs.sh gateway-service"
done
cat <<'INFO'
NebulaOps URLs:
  Frontend:   http://localhost:4200
  Gateway:    http://localhost:8080/actuator/health
  Grafana:    http://localhost:3000  admin/admin
  Prometheus: http://localhost:9090
Run smoke test: ./scripts/wsl/smoke-test.sh
INFO
command -v explorer.exe >/dev/null 2>&1 && explorer.exe http://localhost:4200 >/dev/null 2>&1 || true
