#!/usr/bin/env bash
set -euo pipefail
export COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME:-nebulaops-v23-2}
mkdir -p .kube
if [[ -f "$HOME/.kube/config" ]]; then cp "$HOME/.kube/config" .kube/config; chmod 600 .kube/config; fi
docker compose up -d --build
./scripts/smoke-test.sh || true
echo "Frontend: http://nebulaops.localhost | Gateway: http://localhost:8080 | Grafana: http://localhost:${GRAFANA_HOST_PORT:-3300} admin/admin"
