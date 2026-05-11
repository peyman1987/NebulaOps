#!/usr/bin/env bash
# NebulaOps v23.4 — end-to-end runtime smoke checks.
# Validates that core runtime endpoints return JSON contracts, not HTML/error pages,
# and that unavailable live integrations are explicit instead of silently fabricated.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$ROOT_DIR/scripts/wsl/lib/smoke-runtime-lib.sh"

say "▶ NebulaOps v23.4 E2E runtime smoke"
say "  baseUrl=${NEBULAOPS_BASE_URL}"

require_tool curl
require_tool python3

say "\n1) Static live-only guard"
python3 "$ROOT_DIR/scripts/verify-live-only-runtime.py"
ok "no bundled mock/static operational records detected"

say "\n2) Core JSON endpoints must not return HTML/error pages"
assert_json_endpoint GET "/actuator/health" "gateway actuator health" "" 12
assert_json_endpoint GET "/api/runtime/diagnostics" "runtime diagnostics" "" 18
assert_json_endpoint GET "/api/runtime/diagnostics/bundle" "diagnostics bundle manifest" "" 30
assert_json_endpoint GET "/api/platform/issues" "operational issues" "" 20
assert_json_endpoint GET "/api/platform/issues/summary" "operational issues summary" "" 20
assert_json_endpoint GET "/api/extensions" "extension registry" "" 10

say "\n3) /api/extensions must not regress to gateway timeout"
assert_status_under GET "/api/extensions" "extension registry latency guard" 10

say "\n4) Docker runtime endpoints: real collections or explicit Docker status"
assert_json_endpoint GET "/api/runtime/docker/status" "Docker status" "" 15
assert_json_body_semantics "/api/runtime/docker/status" "Docker status"
assert_json_endpoint GET "/api/runtime/docker/containers" "Docker containers" "" 20
assert_json_endpoint GET "/api/runtime/docker/images" "Docker images" "" 20
assert_json_endpoint GET "/api/runtime/docker/networks" "Docker networks" "" 20
assert_json_endpoint GET "/api/runtime/docker/events" "Docker events" "" 20

say "\n5) Kubernetes runtime endpoints: real data or explicit UNAVAILABLE/DEGRADED state"
assert_json_endpoint GET "/api/kubernetes/namespaces" "Kubernetes namespaces" "" 20
assert_json_body_semantics "/api/kubernetes/namespaces" "Kubernetes namespaces"
assert_json_endpoint GET "/api/kubernetes/resources?kind=pods&namespace=all" "Kubernetes pods" "" 20
assert_json_body_semantics "/api/kubernetes/resources?kind=pods&namespace=all" "Kubernetes pods"
assert_json_endpoint GET "/api/kubernetes/events?namespace=all" "Kubernetes events" "" 20
assert_json_body_semantics "/api/kubernetes/events?namespace=all" "Kubernetes events"
assert_json_endpoint GET "/api/kubernetes/helm/releases?namespace=all" "Helm releases" "" 20
assert_json_body_semantics "/api/kubernetes/helm/releases?namespace=all" "Helm releases"

say "\n6) Safe Action Plan endpoints must respond with JSON contracts"
assert_json_endpoint POST "/api/runtime/docker/actions/plan" "Docker Safe Action Plan" '{"action":"system.prune","target":"docker","parameters":{"volumes":false}}' 20
assert_json_endpoint POST "/api/kubernetes/actions/plan" "Kubernetes Safe Action Plan" '{"action":"pod.delete","target":"default/non-existent-smoke-pod","parameters":{"namespace":"default","name":"non-existent-smoke-pod"}}' 20

say "\nNebulaOps v23.4 runtime E2E smoke OK"
