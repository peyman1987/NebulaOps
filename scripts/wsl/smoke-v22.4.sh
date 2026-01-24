#!/usr/bin/env bash
# NebulaOps v22.4 smoke test: validates the new v22.4 API surface through the gateway.
set -uo pipefail

BASE="${NEBULAOPS_GATEWAY_URL:-http://localhost:8080}"
TOKEN="${NEBULAOPS_TOKEN:-}"

AUTH_HEADER=()
if [ -n "$TOKEN" ]; then
  AUTH_HEADER=(-H "Authorization: Bearer $TOKEN")
fi

ok=0
fail=0

check_get() {
  local label="$1"
  local path="$2"
  local code
  code=$(curl -sS --max-time 8 -o /tmp/nebulaops-smoke.out -w "%{http_code}" "${AUTH_HEADER[@]}" "$BASE$path" 2>/dev/null || true)
  if [[ "$code" =~ ^(200|201|202|204)$ ]]; then
    printf "  \033[32m●\033[0m %-34s %s\n" "$label" "$path"
    ok=$((ok+1))
  else
    printf "  \033[31m●\033[0m %-34s HTTP %s %s\n" "$label" "${code:-000}" "$path"
    fail=$((fail+1))
  fi
}

check_post() {
  local label="$1"
  local path="$2"
  local body="${3:-{}}"
  local code
  code=$(curl -sS --max-time 8 -o /tmp/nebulaops-smoke.out -w "%{http_code}" \
    "${AUTH_HEADER[@]}" -H "Content-Type: application/json" -d "$body" "$BASE$path" 2>/dev/null || true)
  if [[ "$code" =~ ^(200|201|202|204)$ ]]; then
    printf "  \033[32m●\033[0m %-34s %s\n" "$label" "$path"
    ok=$((ok+1))
  else
    printf "  \033[31m●\033[0m %-34s HTTP %s %s\n" "$label" "${code:-000}" "$path"
    fail=$((fail+1))
  fi
}

echo "[NebulaOps] v22.4 gateway smoke test"
echo "Gateway: $BASE"
[ -z "$TOKEN" ] && echo "[WARN] NEBULAOPS_TOKEN not set. Protected deployments may return 401/403."

check_get  "Gateway health"            "/actuator/health"
check_get  "Release list"              "/api/releases"
check_post "Release promote"           "/api/releases/rel-22.4-001/promote" '{"targetEnvironment":"local"}'
check_post "Release rollback"          "/api/releases/rel-22.4-001/rollback" '{"revision":"previous"}'
check_get  "Policy list"               "/api/policies"
check_post "Policy evaluate"           "/api/policies/evaluate" '{"target":"nebulaops-platform"}'
check_get  "Platform events"           "/api/events"
check_get  "Notification live"        "/api/notifications/live"
check_get  "Notification preferences" "/api/notifications/preferences"
check_post "Platform event record"     "/api/events" '{"type":"SMOKE_TEST","source":"smoke-v22.4","actor":"local","correlationId":"smoke-v22.4"}'
check_post "AI incident analysis"      "/api/ai-ops/incidents/analyze" '{"source":"smoke","affectedService":"gateway-service"}'
check_get  "DevSecOps vulnerabilities" "/api/devsecops/vulnerabilities"
check_get  "Cost services"             "/api/cost/services"
check_get  "Kubernetes events"         "/api/kubernetes/events"

echo
echo "OK=$ok FAIL=$fail"
[ "$fail" -eq 0 ]
