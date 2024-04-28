#!/usr/bin/env bash
set -euo pipefail
BASE=${BASE:-http://localhost:8080}
echo "Registering demo user..."
curl -fsS -H 'Content-Type: application/json' -d '{"email":"demo@nebulaops.dev","displayName":"Demo User","password":"Password123!","organizationId":"demo-org"}' "$BASE/api/auth/register" || true
echo
echo "Creating task through gateway..."
curl -fsS -H 'Content-Type: application/json' -d '{"organizationId":"demo-org","projectId":"portfolio","title":"WSL smoke test task","description":"Created from scripts/wsl/smoke-test.sh","priority":"HIGH","labels":["wsl","rabbitmq","mongodb"]}' "$BASE/api/tasks"
echo; echo "Tasks:"; curl -fsS "$BASE/api/tasks?organizationId=demo-org"
echo; echo "Notifications:"; curl -fsS "$BASE/api/notifications" || true
echo; echo "Smoke test completed."


echo "Checking Go cache service"
curl -fsS http://localhost:8091/health >/dev/null && echo "go-cache-service OK"


echo "Checking Grafana..."
if ! curl -fsS http://localhost:3000/api/health >/dev/null; then
  echo "FAIL Grafana is not healthy. Recent logs:"
  docker compose logs --tail=80 grafana || true
  exit 1
fi
echo "OK Grafana is healthy"
