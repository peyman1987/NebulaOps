#!/usr/bin/env bash
# v21.2 — Comprehensive platform health check.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

log_step "Container status"
dc ps --format "table {{.Service}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null \
  | head -40 || log_warn "Compose not available"

log_step "Endpoint health"

check_endpoint() {
  local label="$1" url="$2"
  if curl -fsS --max-time 3 "$url" >/dev/null 2>&1; then
    printf "  ${C_GREEN}●${C_RESET} %-22s %s\n" "$label" "$url"
  else
    printf "  ${C_RED}●${C_RESET} %-22s %s\n" "$label" "$url"
  fi
}

check_endpoint "Frontend"      "http://localhost:4200"
check_endpoint "Gateway"       "http://localhost:8080/actuator/health"
check_endpoint "Gateway /api"  "http://localhost:8080/api/health"
check_endpoint "Auth"          "http://localhost:8081/actuator/health"
check_endpoint "Task"          "http://localhost:8082/actuator/health"
check_endpoint "Notification"  "http://localhost:8083/actuator/health"
check_endpoint "File"          "http://localhost:8084/actuator/health"
check_endpoint "AI Ops"        "http://localhost:8085/actuator/health"
check_endpoint "DevSecOps"     "http://localhost:8086/actuator/health"
check_endpoint "Pipeline"      "http://localhost:8087/actuator/health"
check_endpoint "Observability" "http://localhost:8092/actuator/health"
check_endpoint "GitOps"        "http://localhost:8093/actuator/health"
check_endpoint "EnvManager"    "http://localhost:8094/actuator/health"
check_endpoint "AI Engine"     "http://localhost:8095/healthz"
check_endpoint "Terraform"     "http://localhost:8096/actuator/health"
check_endpoint "Go Cache"      "http://localhost:8091/healthz"
check_endpoint "Grafana"       "http://localhost:3000/api/health"
check_endpoint "Prometheus"    "http://localhost:9090/-/healthy"
check_endpoint "Loki"          "http://localhost:3100/ready"
check_endpoint "RabbitMQ"      "http://localhost:15672/"
check_endpoint "MongoDB UI"    "http://localhost:8088"

log_step "Sample API smoke test (gateway → microservices)"
check_endpoint "Tasks list"        "http://localhost:8080/api/tasks?organizationId=default-org"
check_endpoint "K8s snapshot"      "http://localhost:8080/api/kubernetes/snapshot"
check_endpoint "Docker containers" "http://localhost:8080/api/runtime/docker/containers"
check_endpoint "Helm releases"     "http://localhost:8080/api/runtime/helm/releases?namespace=all"
check_endpoint "Environments"      "http://localhost:8080/api/platform/environments"

echo
