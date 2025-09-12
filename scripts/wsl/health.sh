#!/usr/bin/env bash
# v21.4 — Comprehensive platform health check.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

log_step "Container status"
dc ps --format "table {{.Service}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null \
  | head -40 || log_warn "Compose not available"

log_step "Endpoint health"


check_keycloak_login() {
  local label="Keycloak custom login"
  local tmp="/tmp/nebulaops-keycloak-auth-health.html"
  local challenge="xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
  local url="http://localhost:8180/realms/nebulaops/protocol/openid-connect/auth?client_id=nebulaops-frontend&redirect_uri=http%3A%2F%2Flocalhost%3A4200&response_type=code&scope=openid%20profile%20email&state=healthcheck&code_challenge=${challenge}&code_challenge_method=S256"
  local code
  code=$(curl -sS --max-time 5 -o "$tmp" -w "%{http_code}" "$url" 2>/dev/null || true)
  if [ "$code" = "200" ] && grep -qiE "kc-form-login|NebulaOps v21\.4|name=\"username\"" "$tmp" 2>/dev/null; then
    printf "  ${C_GREEN}●${C_RESET} %-22s %s\n" "$label" "HTTP 200 login page"
  else
    printf "  ${C_RED}●${C_RESET} %-22s %s\n" "$label" "HTTP ${code:-000}"
    if [ "$code" = "500" ]; then
      printf "    ${C_YELLOW}↳${C_RESET} Custom theme failed. Run: docker compose -p $PROJECT_NAME logs keycloak --tail=160 | grep -iE 'freemarker|template|ERROR'\n"
    fi
  fi
}

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
check_endpoint "AI Engine"     "http://localhost:8095/health"
check_endpoint "Terraform"     "http://localhost:8096/actuator/health"
check_endpoint "Go Cache"      "http://localhost:8091/health"
check_endpoint "Grafana"       "http://localhost:3000/api/health"
check_endpoint "Prometheus"    "http://localhost:9090/-/healthy"
check_endpoint "Loki"          "http://localhost:3100/loki/api/v1/status/buildinfo"
check_endpoint "RabbitMQ"      "http://guest:guest@localhost:15672/api/overview"
check_endpoint "MongoDB UI"    "http://admin:admin@localhost:8088"
check_endpoint "Keycloak"      "http://localhost:8180/health/ready"
check_keycloak_login

log_step "Sample API smoke test (gateway → microservices)"
check_endpoint "Tasks list"        "http://localhost:8080/api/tasks?organizationId=default-org"
check_endpoint "K8s snapshot"      "http://localhost:8080/api/kubernetes/snapshot"
check_endpoint "Docker containers" "http://localhost:8080/api/runtime/docker/containers"
check_endpoint "Helm releases"     "http://localhost:8080/api/runtime/helm/releases?namespace=all"
check_endpoint "Environments"      "http://localhost:8080/api/platform/environments"

echo
