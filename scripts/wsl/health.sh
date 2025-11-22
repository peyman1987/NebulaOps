#!/usr/bin/env bash
# v22.2 — Comprehensive platform health check with Keycloak-aware API and SSO proxy checks.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

SMOKE_USER="${NEBULAOPS_SMOKE_USER:-admin}"
SMOKE_PASSWORD="${NEBULAOPS_SMOKE_PASSWORD:-admin}"
TOKEN=""

running_service() {
  dc ps --services --filter status=running 2>/dev/null | grep -qx "$1"
}

print_ok()   { printf "  ${C_GREEN}●${C_RESET} %-22s %s\n" "$1" "$2"; }
print_warn() { printf "  ${C_YELLOW}●${C_RESET} %-22s %s\n" "$1" "$2"; }
print_bad()  { printf "  ${C_RED}●${C_RESET} %-22s %s\n" "$1" "$2"; }
print_skip() { printf "  ${C_DIM}○${C_RESET} %-22s %s\n" "$1" "$2"; }

keycloak_user_token() {
  curl -fsS --max-time 8 \
    -X POST "http://localhost:8180/realms/nebulaops/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "username=$SMOKE_USER" \
    --data-urlencode "password=$SMOKE_PASSWORD" \
    --data-urlencode 'grant_type=password' \
    --data-urlencode 'client_id=nebulaops-frontend' 2>/dev/null \
  | python3 -c 'import json,sys; print(json.load(sys.stdin).get("access_token", ""))' 2>/dev/null || true
}

check_endpoint() {
  local label="$1" url="$2"
  if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then
    print_ok "$label" "$url"
  else
    print_bad "$label" "$url"
  fi
}

check_optional_endpoint() {
  local label="$1" service="$2" url="$3"
  if running_service "$service"; then
    check_endpoint "$label" "$url"
  else
    print_skip "$label" "disabled profile ($service not running)"
  fi
}

check_host_or_running_service() {
  local label="$1" service="$2" url="$3"
  if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then
    print_ok "$label" "$url"
  elif running_service "$service"; then
    print_ok "$label" "container running; host endpoint not exposed yet: $url"
  else
    print_bad "$label" "$url"
  fi
}

check_authed_endpoint() {
  local label="$1" url="$2"
  if [ -z "$TOKEN" ]; then
    print_warn "$label" "no Keycloak token; cannot test $url"
    return 0
  fi
  local code
  code=$(curl -sS --max-time 8 -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$url" 2>/dev/null || true)
  case "$code" in
    200|201|202|204) print_ok "$label" "$url" ;;
    401|403) print_bad "$label" "HTTP $code even with Keycloak token: $url" ;;
    000|'') print_bad "$label" "not reachable: $url" ;;
    *) print_bad "$label" "HTTP $code: $url" ;;
  esac
}

check_sso_redirect() {
  local label="$1" service="$2" url="$3"
  if ! running_service "$service"; then
    print_skip "$label" "SSO profile disabled ($service not running)"
    return 0
  fi
  local tmp code location
  tmp="$(mktemp)"
  code=$(curl -sS --max-time 8 -o /dev/null -D "$tmp" -w '%{http_code}' "$url" 2>/dev/null || true)
  location=$(awk 'BEGIN{IGNORECASE=1} /^location:/ {sub(/^[Ll]ocation:[[:space:]]*/, ""); gsub(/\r/, ""); print; exit}' "$tmp" 2>/dev/null || true)
  rm -f "$tmp"
  case "$code" in
    200|202)
      print_ok "$label" "SSO endpoint reachable: $url"
      ;;
    301|302|303|307|308)
      if printf '%s' "$location" | grep -qiE '(/oauth2/(start|sign_in)|localhost:8180|keycloak)'; then
        print_ok "$label" "SSO redirect OK -> Keycloak/OAuth2 login"
      else
        print_bad "$label" "HTTP $code unexpected Location=${location:-none}"
      fi
      ;;
    401|403)
      print_warn "$label" "protected by SSO (HTTP $code): $url"
      ;;
    *)
      print_bad "$label" "HTTP ${code:-000}: $url"
      ;;
  esac
}

check_basic_proxy_upstream() {
  local label="$1" service="$2"
  if ! running_service "$service"; then
    print_skip "$label" "SSO bridge disabled ($service not running)"
    return 0
  fi
  local self upstream
  self=$(dc exec -T "$service" sh -c "wget -q -O - http://127.0.0.1:8080/__nebulaops_proxy_health 2>/dev/null" 2>/dev/null || true)
  if ! printf '%s' "$self" | grep -q 'ok'; then
    print_bad "$label" "bridge nginx health endpoint not reachable; run ./scripts/wsl/diagnose-sso.sh"
    return 0
  fi
  upstream=$(dc exec -T "$service" sh -c "wget -q -S -O /dev/null http://127.0.0.1:8080/ 2>&1 | awk '/HTTP\// {c=\$2} END {print c}'" 2>/dev/null || true)
  case "$upstream" in
    200|201|202|204|301|302|303|307|308) print_ok "$label" "bridge OK, upstream HTTP $upstream" ;;
    401|403) print_warn "$label" "bridge OK, upstream protected HTTP $upstream" ;;
    502) print_bad "$label" "bridge OK, but upstream BAD GATEWAY; run ./scripts/wsl/diagnose-sso.sh" ;;
    000|'') print_warn "$label" "bridge OK, upstream still warming up; run ./scripts/wsl/diagnose-sso.sh if browser shows 502" ;;
    *) print_warn "$label" "bridge OK, upstream HTTP ${upstream:-000}" ;;
  esac
}

check_keycloak_login() {
  local label="Keycloak custom login"
  local tmp="/tmp/nebulaops-keycloak-auth-health.html"
  local challenge="xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
  local url="http://localhost:8180/realms/nebulaops/protocol/openid-connect/auth?client_id=nebulaops-frontend&redirect_uri=http%3A%2F%2Flocalhost%3A4200&response_type=code&scope=openid%20profile%20email&state=healthcheck&code_challenge=${challenge}&code_challenge_method=S256"
  local code
  code=$(curl -sS --max-time 8 -o "$tmp" -w "%{http_code}" "$url" 2>/dev/null || true)
  if [ "$code" = "200" ] && grep -qiE "kc-form-login|NebulaOps v22\.2|name=\"username\"" "$tmp" 2>/dev/null; then
    print_ok "$label" "HTTP 200 login page"
  else
    print_bad "$label" "HTTP ${code:-000}; run ./scripts/keycloak-ensure-sso-clients.sh"
  fi
}

log_step "Container status"
dc ps --format "table {{.Service}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null \
  | head -60 || log_warn "Compose not available"

log_step "Endpoint health"
TOKEN="$(keycloak_user_token)"
if [ -n "$TOKEN" ]; then
  print_ok "Keycloak token" "obtained for $SMOKE_USER"
else
  print_warn "Keycloak token" "not available yet; API checks may show warnings"
fi

check_endpoint "Frontend shell" "http://localhost:4200"
check_endpoint "MFE Docker" "http://localhost:4211/remoteEntry.js"
check_endpoint "MFE OpenLens" "http://localhost:4212/remoteEntry.js"
check_endpoint "MFE Tasks" "http://localhost:4213/remoteEntry.js"
check_endpoint "MFE Observe" "http://localhost:4214/remoteEntry.js"
check_endpoint "MFE CI/CD" "http://localhost:4215/remoteEntry.js"
check_endpoint "MFE Terraform" "http://localhost:4216/remoteEntry.js"
check_endpoint "MFE Security" "http://localhost:4217/remoteEntry.js"
check_endpoint "MFE AI Ops" "http://localhost:4218/remoteEntry.js"
check_endpoint "MFE FinOps" "http://localhost:4219/remoteEntry.js"
check_endpoint "Gateway"       "http://localhost:8080/actuator/health"
check_authed_endpoint "Gateway /api"  "http://localhost:8080/api/health"
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
if running_service prometheus-sso; then
  check_sso_redirect "Prometheus" "prometheus-sso" "http://localhost:9090/"
else
  check_host_or_running_service "Prometheus" "prometheus" "http://localhost:9090/-/healthy"
fi
check_endpoint "Loki"          "http://localhost:3100/loki/api/v1/status/buildinfo"

if running_service rabbitmq-management-sso; then
  check_sso_redirect "RabbitMQ" "rabbitmq-management-sso" "http://localhost:15672/"
  check_basic_proxy_upstream "RabbitMQ backend" "rabbitmq-basic-proxy"
else
  check_endpoint "RabbitMQ" "http://guest:guest@localhost:15672/api/overview"
fi

if running_service mongo-express-sso; then
  check_sso_redirect "MongoDB UI" "mongo-express-sso" "http://localhost:8088/"
  check_basic_proxy_upstream "MongoDB backend" "mongo-express-basic-proxy"
else
  check_endpoint "MongoDB UI" "http://admin:admin@localhost:8088"
fi

if running_service redis-commander-sso; then
  check_sso_redirect "Redis UI" "redis-commander-sso" "http://localhost:8089/"
  check_basic_proxy_upstream "Redis backend" "redis-commander-basic-proxy"
else
  check_endpoint "Redis UI" "http://admin:admin@localhost:8089"
fi

check_endpoint "Keycloak"      "http://localhost:8180/health/ready"
check_optional_endpoint "GitLab" "gitlab" "http://localhost:8929/-/health"
check_keycloak_login

log_step "Sample API smoke test (gateway → microservices, Keycloak token)"
check_authed_endpoint "Tasks list"        "http://localhost:8080/api/tasks?organizationId=default-org"
check_authed_endpoint "K8s snapshot"      "http://localhost:8080/api/kubernetes/snapshot"
check_authed_endpoint "Docker containers" "http://localhost:8080/api/runtime/docker/containers"
check_authed_endpoint "Helm releases"     "http://localhost:8080/api/runtime/helm/releases?namespace=all"
check_authed_endpoint "Environments"      "http://localhost:8080/api/platform/environments"

echo
