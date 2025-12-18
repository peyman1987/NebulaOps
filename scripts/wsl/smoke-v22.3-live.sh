#!/usr/bin/env bash
# NebulaOps v22.3 live integration smoke test.
set -uo pipefail

BASE="${NEBULAOPS_GATEWAY_URL:-http://localhost:8080}"
TOKEN="${NEBULAOPS_TOKEN:-}"
AUTH_HEADER=()
if [ -n "$TOKEN" ]; then AUTH_HEADER=(-H "Authorization: Bearer $TOKEN"); fi

ok=0
fail=0

check_get() {
  local label="$1"; local path="$2"; local code
  code=$(curl -sS --max-time 10 -o /tmp/nebulaops-live-smoke.out -w "%{http_code}" "${AUTH_HEADER[@]}" "$BASE$path" 2>/dev/null || true)
  if [[ "$code" =~ ^(200|201|202|204)$ ]]; then
    printf "  \033[32m●\033[0m %-38s %s\n" "$label" "$path"; ok=$((ok+1))
  else
    printf "  \033[31m●\033[0m %-38s HTTP %s %s\n" "$label" "${code:-000}" "$path"; fail=$((fail+1))
  fi
}

check_post() {
  local label="$1"; local path="$2"; local body="${3:-{}}"; local code
  code=$(curl -sS --max-time 10 -o /tmp/nebulaops-live-smoke.out -w "%{http_code}" "${AUTH_HEADER[@]}" \
    -H "Content-Type: application/json" -d "$body" "$BASE$path" 2>/dev/null || true)
  if [[ "$code" =~ ^(200|201|202|204)$ ]]; then
    printf "  \033[32m●\033[0m %-38s %s\n" "$label" "$path"; ok=$((ok+1))
  else
    printf "  \033[31m●\033[0m %-38s HTTP %s %s\n" "$label" "${code:-000}" "$path"; fail=$((fail+1))
  fi
}

echo "[NebulaOps] v22.3 LIVE integration smoke test"
echo "Gateway: $BASE"
[ -z "$TOKEN" ] && echo "[WARN] NEBULAOPS_TOKEN not set. Protected deployments may return 401/403."

check_get  "Release list + live signals"       "/api/releases"
check_post "Release promotion policy gate"     "/api/releases/rel-22.3-001/promote" '{"targetEnvironment":"local","budgetThreshold":75}'
check_post "Release rollback audit event"      "/api/releases/rel-22.3-001/rollback" '{"revision":"previous"}'
check_post "Policy evaluation live"            "/api/policies/evaluate" '{"target":"gateway-service","image":"nebulaops-v22-3-gateway-service:22.3.0","budgetThreshold":75}'
check_post "AI Ops live RCA"                   "/api/ai-ops/incidents/analyze" '{"affectedService":"gateway-service","namespace":"default"}'
check_post "DevSecOps image scan + audit"      "/api/devsecops/scan/image" '{"image":"nebulaops-v22-3-gateway-service:22.3.0"}'
check_get  "Cost forecast threshold"           "/api/cost/forecast?threshold=75"
check_get  "Kubernetes events"                 "/api/kubernetes/events?namespace=default"
check_get  "Notification list"                 "/api/notifications/live"
check_get  "Audit/event timeline"              "/api/events"
check_get  "Docs index"                        "/api/docs"

echo
echo "OK=$ok FAIL=$fail"
[ "$fail" -eq 0 ]
