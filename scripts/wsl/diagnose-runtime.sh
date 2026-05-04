#!/usr/bin/env bash
set -euo pipefail
PROJECT_NAME=${COMPOSE_PROJECT_NAME:-nebulaops-v23-3}
SERVICES=(gateway-service devsecops-service pipeline-engine-service gitops-control-service environment-manager-service terraform-studio-service ai-ops-service)
for svc in "${SERVICES[@]}"; do
  cid=$(docker compose -p "$PROJECT_NAME" ps -q "$svc" 2>/dev/null || true)
  echo "--- $svc ---"
  if [[ -z "$cid" ]]; then
    echo "container not found"
    continue
  fi
  docker exec "$cid" sh -lc 'echo PATH=$PATH; /opt/java/openjdk/bin/java -version 2>&1 | head -1; command -v kubectl || true; command -v docker || true; command -v helm || true; command -v trivy || true; command -v terraform || true; command -v argocd || true' || true
done
