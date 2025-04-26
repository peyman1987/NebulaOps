#!/usr/bin/env bash
# Show gateway-service startup logs — helps diagnose 502 issues
PROJECT=${1:-nebulaops-v21-1}
echo "=== Gateway container status ==="
docker compose -p "$PROJECT" ps gateway-service
echo ""
echo "=== Gateway logs (last 100 lines) ==="
docker compose -p "$PROJECT" logs --tail=100 gateway-service
echo ""
echo "=== Testing gateway health directly ==="
curl -sv http://localhost:8080/actuator/health 2>&1 | tail -20
