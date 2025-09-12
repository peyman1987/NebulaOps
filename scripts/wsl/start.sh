#!/usr/bin/env bash
# v21.4 — NebulaOps startup script with custom Keycloak OIDC login auto-check.
# Usage: ./scripts/wsl/start.sh [--rebuild-gateway]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

REBUILD_GATEWAY=false
for arg in "$@"; do
  case "$arg" in
    --rebuild-gateway) REBUILD_GATEWAY=true ;;
    -h|--help)
      cat <<USAGE
Usage: $0 [--rebuild-gateway]
  --rebuild-gateway    Force no-cache rebuild of the gateway-service image
                       (use when gateway routes/config changed)
USAGE
      exit 0
      ;;
  esac
done

cd "$ROOT_DIR"

keycloak_theme_dir() {
  printf '%s\n' "$ROOT_DIR/infrastructure/keycloak/themes/nebulaops/login"
}

write_custom_keycloak_login_theme() {
  local theme_dir
  theme_dir="$(keycloak_theme_dir)"
  mkdir -p "$theme_dir/resources/css"

  # Important: do NOT ship/keep a custom template.ftl here.
  # Keycloak parent pages can then safely use the parent theme template,
  # while NebulaOps login.ftl stays fully standalone.
  rm -f "$theme_dir/template.ftl"

  cat > "$theme_dir/theme.properties" <<'THEME'
parent=keycloak
import=common/keycloak
styles=css/nebulaops.css
locales=en,it
THEME

  cat > "$theme_dir/login.ftl" <<'FTL'
<#-- NebulaOps v21.4 standalone Keycloak login page. No template.ftl import. -->
<#assign nbLoginAction="">
<#assign nbUsername="">
<#assign nbRemember=false>
<#assign nbForgot=false>
<#assign nbShowUsername=true>
<#assign nbMessageType="info">
<#assign nbMessageSummary="">
<#assign nbSelectedCredential="">

<#if url?? && url.loginAction??><#assign nbLoginAction=url.loginAction></#if>
<#if login?? && login.username??><#assign nbUsername=login.username></#if>
<#if realm?? && realm.rememberMe?? && realm.rememberMe><#assign nbRemember=true></#if>
<#if realm?? && realm.resetPasswordAllowed?? && realm.resetPasswordAllowed && url?? && url.loginResetCredentialsUrl??><#assign nbForgot=true></#if>
<#if usernameHidden?? && usernameHidden><#assign nbShowUsername=false></#if>
<#if message?? && message.type??><#assign nbMessageType=message.type></#if>
<#if message?? && message.summary??><#assign nbMessageSummary=message.summary></#if>
<#if auth?? && auth.selectedCredential??><#assign nbSelectedCredential=auth.selectedCredential></#if>
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="robots" content="noindex, nofollow">
  <title>NebulaOps v21.4 Login</title>
  <style>
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
    html, body { min-height: 100%; }
    body {
      min-height: 100vh;
      background:
        radial-gradient(circle at 15% 18%, rgba(34,211,238,.22) 0%, transparent 28%),
        radial-gradient(circle at 85% 10%, rgba(139,92,246,.28) 0%, transparent 30%),
        linear-gradient(135deg, #020617 0%, #070a18 55%, #111026 100%);
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Arial, sans-serif;
      color: #eef6ff;
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      overflow: hidden;
      padding: 24px;
    }
    body::before {
      content: '';
      position: fixed;
      inset: 0;
      background-image:
        linear-gradient(rgba(125,211,252,.055) 1px, transparent 1px),
        linear-gradient(90deg, rgba(125,211,252,.055) 1px, transparent 1px);
      background-size: 70px 70px;
      pointer-events: none;
    }
    .nb-card {
      position: relative;
      width: min(520px, 92vw);
      padding: 44px 48px;
      border: 1px solid rgba(148,211,255,.22);
      border-radius: 32px;
      background: linear-gradient(145deg, rgba(11,18,38,.94), rgba(18,18,49,.86));
      box-shadow: 0 48px 130px rgba(0,0,0,.65), inset 0 1px 0 rgba(255,255,255,.07);
      backdrop-filter: blur(28px);
    }
    .nb-brand { display: flex; align-items: center; gap: 16px; margin-bottom: 8px; }
    .nb-logo {
      display: grid; place-items: center;
      width: 56px; height: 56px; border-radius: 18px;
      background: linear-gradient(135deg, #22d3ee, #8b5cf6);
      font-size: 13px; font-weight: 1000; color: #fff;
      box-shadow: 0 0 32px rgba(34,211,238,.38);
      flex-shrink: 0;
    }
    .nb-brand-text p { margin: 0; color: #7dd3fc; font-size: 10px; font-weight: 900; text-transform: uppercase; letter-spacing: .18em; }
    .nb-brand-text h1 { margin: 2px 0 0; color: #fff; font-size: 22px; font-weight: 900; letter-spacing: -.03em; }
    .nb-lead { color: #94a3b8; font-size: 13px; line-height: 1.5; margin-bottom: 28px; margin-top: 4px; }
    .nb-divider { height: 1px; background: rgba(255,255,255,.08); margin: 0 -48px 28px; }
    .nb-title { color: #e2e8f0; font-size: 18px; font-weight: 900; margin-bottom: 18px; }
    .nb-field-group { margin-bottom: 18px; }
    .nb-field-group label { display: block; margin-bottom: 8px; color: #cde7ff; font-size: 12px; font-weight: 800; letter-spacing: .04em; text-transform: uppercase; }
    .nb-field-group input[type="text"], .nb-field-group input[type="password"] {
      width: 100%; padding: 13px 16px; border-radius: 14px;
      border: 1px solid rgba(148,211,255,.18); background: rgba(3,7,18,.7);
      color: #fff; font-family: inherit; font-size: 14px; font-weight: 600; outline: none;
    }
    .nb-field-group input:focus { border-color: rgba(34,211,238,.5); box-shadow: 0 0 0 3px rgba(34,211,238,.1); }
    .nb-form-options { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 4px 0 24px; }
    .nb-checkbox { display: flex; align-items: center; gap: 8px; color: #94a3b8; font-size: 12px; font-weight: 600; cursor: pointer; }
    .nb-forgot-link { color: #22d3ee; font-size: 12px; font-weight: 700; text-decoration: none; }
    .nb-submit-btn { width: 100%; padding: 15px 24px; border: none; border-radius: 999px; background: linear-gradient(135deg, #20c8ff, #7c3aed); color: #fff; font-family: inherit; font-size: 15px; font-weight: 900; cursor: pointer; box-shadow: 0 16px 44px rgba(34,211,238,.22); }
    .nb-alert { padding: 12px 16px; border-radius: 14px; font-size: 13px; font-weight: 650; margin-bottom: 20px; }
    .nb-alert-error { border: 1px solid rgba(248,113,113,.35); background: rgba(127,29,29,.22); color: #fecaca; }
    .nb-alert-success { border: 1px solid rgba(34,211,238,.3); background: rgba(8,47,73,.4); color: #67e8f9; }
    .nb-alert-warning { border: 1px solid rgba(251,191,36,.3); background: rgba(120,53,15,.25); color: #fde68a; }
    .nb-alert-info { border: 1px solid rgba(96,165,250,.3); background: rgba(30,58,138,.25); color: #bfdbfe; }
    .nb-footer { margin-top: 28px; padding-top: 18px; border-top: 1px solid rgba(255,255,255,.06); color: #334155; font-size: 10px; font-weight: 800; text-align: center; letter-spacing: .1em; text-transform: uppercase; }
    @media (max-width: 560px) { .nb-card { padding: 34px 26px; } .nb-divider { margin-left: -26px; margin-right: -26px; } }
  </style>
</head>
<body>
  <main class="nb-card">
    <div class="nb-brand">
      <div class="nb-logo">N21.4</div>
      <div class="nb-brand-text">
        <p>Terraform enabled SaaS cockpit</p>
        <h1>NebulaOps v21.4</h1>
      </div>
    </div>
    <p class="nb-lead">DevOps portfolio platform - Docker · Kubernetes · Helm · Terraform · GitOps</p>
    <div class="nb-divider"></div>
    <div class="nb-title">Sign in to NebulaOps</div>

    <#if nbMessageSummary?has_content>
      <div class="nb-alert nb-alert-${nbMessageType?html}">${nbMessageSummary?html}</div>
    </#if>

    <form id="kc-form-login" onsubmit="document.getElementById('kc-login').disabled = true; return true;" action="${nbLoginAction?html}" method="post">
      <#if nbShowUsername>
        <div class="nb-field-group">
          <label for="username">Username or email</label>
          <input tabindex="1" id="username" name="username" value="${nbUsername?html}" type="text" autofocus autocomplete="username" placeholder="admin">
        </div>
      </#if>

      <div class="nb-field-group">
        <label for="password">Password</label>
        <input tabindex="2" id="password" name="password" type="password" autocomplete="current-password" placeholder="••••••••">
      </div>

      <div class="nb-form-options">
        <#if nbRemember && nbShowUsername>
          <label class="nb-checkbox" for="rememberMe">
            <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox" <#if login?? && login.rememberMe??>checked</#if>>
            <span>Remember me</span>
          </label>
        <#else>
          <span></span>
        </#if>
        <#if nbForgot>
          <a tabindex="5" href="${url.loginResetCredentialsUrl?html}" class="nb-forgot-link">Forgot password?</a>
        </#if>
      </div>

      <input type="hidden" id="id-hidden-input" name="credentialId" value="${nbSelectedCredential?html}">
      <button tabindex="4" class="nb-submit-btn" name="login" id="kc-login" type="submit">Login</button>
    </form>
    <div class="nb-footer">DevOps Enterprise Cockpit · v21.4 · Local-first</div>
  </main>
</body>
</html>
FTL
}

prepare_keycloak_theme_before_start() {
  local theme_dir login_ftl template_ftl
  theme_dir="$(keycloak_theme_dir)"
  login_ftl="$theme_dir/login.ftl"
  template_ftl="$theme_dir/template.ftl"

  if [ -f "$template_ftl" ]; then
    log_warn "Removing custom Keycloak template.ftl to avoid FreeMarker import crashes"
    rm -f "$template_ftl"
  fi

  if [ ! -f "$login_ftl" ] || grep -Eq '<#(import|include)|<@layout\.' "$login_ftl"; then
    log_warn "Keycloak login.ftl is unsafe or missing; writing standalone CUSTOM NebulaOps login theme"
    write_custom_keycloak_login_theme
  else
    log_ok "Keycloak login theme is standalone"
  fi
}

keycloak_auth_url() {
  local challenge="xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
  printf '%s\n' "http://localhost:8180/realms/nebulaops/protocol/openid-connect/auth?client_id=nebulaops-frontend&redirect_uri=http%3A%2F%2Flocalhost%3A4200&response_type=code&scope=openid%20profile%20email&state=healthcheck&code_challenge=${challenge}&code_challenge_method=S256"
}

keycloak_oidc_probe() {
  KEYCLOAK_OIDC_CODE="000"
  KEYCLOAK_OIDC_BODY="/tmp/nebulaops-keycloak-auth-start.html"
  KEYCLOAK_OIDC_CODE=$(curl -sS --max-time 10 -o "$KEYCLOAK_OIDC_BODY" -w "%{http_code}" "$(keycloak_auth_url)" 2>/dev/null || true)
  [ "$KEYCLOAK_OIDC_CODE" = "200" ] && grep -qiE 'kc-form-login|NebulaOps v21\.4|name="username"' "$KEYCLOAK_OIDC_BODY" 2>/dev/null
}

keycloak_admin_token() {
  curl -fsS --max-time 10 \
    -X POST "http://localhost:8180/realms/master/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'username=admin' \
    --data-urlencode 'password=admin' \
    --data-urlencode 'grant_type=password' \
    --data-urlencode 'client_id=admin-cli' 2>/dev/null \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}

keycloak_set_login_theme() {
  local theme="$1" token
  token="$(keycloak_admin_token || true)"
  if [ -z "$token" ]; then
    log_warn "Cannot get Keycloak admin token; skipping loginTheme=$theme"
    return 1
  fi
  curl -fsS --max-time 10 \
    -X PUT "http://localhost:8180/admin/realms/nebulaops" \
    -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' \
    -d "{\"loginTheme\":\"$theme\",\"accountTheme\":\"keycloak\",\"adminTheme\":\"keycloak\",\"emailTheme\":\"keycloak\"}" >/dev/null
}

keycloak_logs_show_theme_error() {
  dc logs keycloak --tail=250 2>/dev/null | grep -qiE 'freemarker|template\.ftl|getTemplateForImporting|getTemplateForInclusion|Template\.java|InternalServerErrorException'
}

validate_keycloak_oidc_login() {
  log_step "Validating Keycloak OIDC login"
  wait_http "http://localhost:8180/health/ready" 180 "keycloak" || {
    log_warn "Keycloak is not ready yet; run ./scripts/wsl/health.sh after startup"
    return 0
  }

  # Ensure the realm uses the NebulaOps theme when the theme is healthy.
  keycloak_set_login_theme "nebulaops" || true

  if keycloak_oidc_probe; then
    log_ok "Keycloak OIDC login page OK (HTTP 200)"
    return 0
  fi

  log_warn "Keycloak OIDC login returned HTTP ${KEYCLOAK_OIDC_CODE:-000}"

  if [ "${KEYCLOAK_OIDC_CODE:-000}" = "500" ] || keycloak_logs_show_theme_error; then
    log_warn "Detected Keycloak FreeMarker/theme failure; applying built-in self-healing"
    write_custom_keycloak_login_theme
    dc restart keycloak >/dev/null
    wait_http "http://localhost:8180/health/ready" 180 "keycloak after theme self-heal" || true
    keycloak_set_login_theme "nebulaops" || true

    if keycloak_oidc_probe; then
      log_ok "Keycloak OIDC login recovered with NebulaOps theme"
      return 0
    fi

    log_warn "NebulaOps CUSTOM theme still failed (HTTP ${KEYCLOAK_OIDC_CODE:-000}); keeping loginTheme=nebulaops"
    log_warn "Default Keycloak theme fallback is disabled because this package must keep the customized login."
    log_warn "Showing the last theme-related Keycloak logs:"
    dc logs keycloak --tail=180 2>/dev/null | grep -iE 'freemarker|template\.ftl|getTemplateForImporting|getTemplateForInclusion|Template\.java|InternalServerErrorException|ERROR' || true
    return 1
  fi

  log_warn "Keycloak OIDC login still not OK. Inspect: docker compose -p $PROJECT_NAME logs keycloak --tail=120"
}

log_step "Pre-flight checks"
"$ROOT_DIR/scripts/wsl/check-wsl.sh"

log_step "Preparing kubeconfig and runtime tools"
"$ROOT_DIR/scripts/wsl/prepare-kubeconfig-for-docker.sh"
"$ROOT_DIR/scripts/wsl/prepare-runtime-tools.sh"

log_step "Preparing Keycloak login theme"
prepare_keycloak_theme_before_start

log_step "Validating Grafana provisioning"
defaults=$(grep -R "isDefault: true" -n \
  infrastructure/observability/grafana/provisioning/datasources 2>/dev/null | wc -l || true)
if [ "$defaults" -ne 1 ]; then
  log_err "Grafana must have exactly one isDefault datasource (found $defaults)"
  exit 1
fi
log_ok "Grafana datasource provisioning OK"

if [ "$REBUILD_GATEWAY" = "true" ]; then
  log_step "Force-rebuilding gateway-service (no cache)"
  dc build --no-cache gateway-service
fi

log_step "Starting NebulaOps v21.4"
# Ensure shared Docker network exists (external: true in docker-compose.yml)
if ! docker network inspect nebulaops-network &>/dev/null; then
  log_info "Creating shared network: nebulaops-network"
  docker network create nebulaops-network
else
  log_info "Network nebulaops-network already exists"
fi
export COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-2}"
dc up --build -d

validate_keycloak_oidc_login || log_warn "Keycloak custom login is not healthy yet; run ./scripts/wsl/health.sh and inspect keycloak logs"

log_step "Waiting for gateway-service"
wait_http "http://localhost:8080/api/health" 120 "gateway-service" || \
  log_warn "Inspect logs: ./scripts/wsl/logs.sh gateway-service"

cat <<INFO

${C_BOLD}NebulaOps v21.4 is running.${C_RESET}

  ${C_CYAN}Frontend${C_RESET}    http://localhost:4200
  ${C_CYAN}Gateway${C_RESET}     http://localhost:8080/actuator/health
  ${C_CYAN}Grafana${C_RESET}     http://localhost:3000        admin/admin
  ${C_CYAN}Keycloak${C_RESET}    http://localhost:8180        admin/admin
  ${C_CYAN}Prometheus${C_RESET}  http://localhost:9090
  ${C_CYAN}RabbitMQ${C_RESET}    http://localhost:15672       guest/guest
  ${C_CYAN}Mongo${C_RESET}       http://localhost:8088        admin/admin

Useful:  ./scripts/wsl/health.sh        — overall status
         ./scripts/wsl/logs.sh <svc>    — tail service logs
         ./scripts/wsl/restart-gateway.sh — quick gateway restart
         ./scripts/wsl/stop.sh          — shut everything down

INFO

command -v explorer.exe >/dev/null 2>&1 && \
  explorer.exe http://localhost:4200 >/dev/null 2>&1 || true
