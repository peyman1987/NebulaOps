#!/usr/bin/env bash
set -euo pipefail
BASE=${BASE:-http://localhost:8080}
echo "Checking gateway health..."
curl -fsS "$BASE/actuator/health" >/dev/null
printf "Registering demo user... "
curl -fsS -H 'Content-Type: application/json'   -d '{"email":"demo@nebulaops.dev","displayName":"Demo User","password":"Password123!","organizationId":"demo-org"}'   "$BASE/api/auth/register" >/tmp/nebulaops-register.json || true
echo "OK"
echo "Creating task through gateway..."
TASK_RESPONSE=$(curl -fsS -H 'Content-Type: application/json'   -d '{"organizationId":"demo-org","projectId":"portfolio","title":"Smoke test Kafka event","description":"Created by smoke-test.sh","priority":"HIGH","labels":["smoke","kafka","mongodb"]}'   "$BASE/api/tasks")
echo "$TASK_RESPONSE"
echo "Listing tasks..."
curl -fsS "$BASE/api/tasks?organizationId=demo-org"
echo
echo "Smoke test completed. Check notifications with: curl $BASE/api/notifications"


echo "Checking Go cache service"
curl -fsS http://localhost:8091/health >/dev/null && echo "go-cache-service OK"
