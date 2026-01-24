#!/usr/bin/env bash
# v22.4 — NebulaOps startup script with custom Keycloak OIDC login auto-check.
# Usage: ./scripts/wsl/start.sh [--rebuild] [--rebuild-gateway] [--with-gitlab] [--with-sso-proxy] [--skip-preflight]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

REBUILD_ALL=false
REBUILD_GATEWAY=false
WITH_GITLAB=false
WITH_SSO_PROXY=false
RUN_PREFLIGHT=true
for arg in "$@"; do
  case "$arg" in
    --rebuild|--build) REBUILD_ALL=true ;;
    --rebuild-gateway) REBUILD_GATEWAY=true ;;
    --with-gitlab) WITH_GITLAB=true ;;
    --with-sso-proxy|--with-sso-proxies) WITH_SSO_PROXY=true ;;
    --skip-preflight) RUN_PREFLIGHT=false ;;
    -h|--help)
      cat <<USAGE
Usage: $0 [--rebuild] [--rebuild-gateway] [--with-gitlab] [--with-sso-proxy] [--skip-preflight]
  --rebuild            Build backend JARs and Docker images before startup
  --rebuild-gateway    Force no-cache rebuild of the gateway-service image
                       (use when gateway routes/config changed)
  --with-gitlab        Also start the optional heavy GitLab CE service
                       (disabled by default; Keycloak still works)
  --with-sso-proxy     Also start OAuth2 Proxy wrappers for RabbitMQ, Mongo Express
                       and Redis Commander. Prometheus SSO remains optional
                       via COMPOSE_PROFILES=sso-prometheus.
  --skip-preflight      Skip the full static v22.4 preflight. Use only after a
                       successful preflight in the same workspace.
USAGE
      exit 0
      ;;
  esac
done

cd "$ROOT_DIR"

run_integrated_preflight() {
  if [ "$RUN_PREFLIGHT" != "true" ]; then
    log_warn "Full v22.4 preflight skipped by --skip-preflight"
    return 0
  fi

  log_step "Running integrated v22.4 preflight"
  "$ROOT_DIR/scripts/wsl/preflight-v22.4.sh"
}

run_integrated_preflight

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
<#-- NebulaOps v22.4 standalone Keycloak login page. No template.ftl import. -->
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
  <title>NebulaOps v22.4 Login</title>
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
      <div class="nb-logo">N22.4</div>
      <div class="nb-brand-text">
        <p>Terraform enabled SaaS cockpit</p>
        <h1>NebulaOps v22.4</h1>
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
    <div class="nb-footer">DevOps Enterprise Cockpit · v22.4 · Local-first</div>
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
  printf '%s\n' "${KEYCLOAK_PUBLIC_URL}/realms/nebulaops/protocol/openid-connect/auth?client_id=nebulaops-frontend&redirect_uri=http%3A%2F%2Fnebulaops.localhost%2F&response_type=code&scope=openid%20profile%20email&state=healthcheck&code_challenge=${challenge}&code_challenge_method=S256"
}

keycloak_oidc_probe() {
  KEYCLOAK_OIDC_CODE="000"
  KEYCLOAK_OIDC_BODY="/tmp/nebulaops-keycloak-auth-start.html"
  KEYCLOAK_OIDC_HEADERS="/tmp/nebulaops-keycloak-auth-start.headers"
  KEYCLOAK_OIDC_LOCATION=""
  KEYCLOAK_OIDC_CODE=$(curl -sS --max-time 15 -D "$KEYCLOAK_OIDC_HEADERS" -o "$KEYCLOAK_OIDC_BODY" -w "%{http_code}" "$(keycloak_auth_url)" 2>/dev/null || true)
  KEYCLOAK_OIDC_LOCATION=$(awk 'BEGIN{IGNORECASE=1} /^location:/ {sub(/^[Ll]ocation:[[:space:]]*/, ""); gsub(/\r/, ""); print; exit}' "$KEYCLOAK_OIDC_HEADERS" 2>/dev/null || true)
  if [ "$KEYCLOAK_OIDC_CODE" = "200" ] && grep -qiE 'kc-form-login|NebulaOps v22\.3|name="username"' "$KEYCLOAK_OIDC_BODY" 2>/dev/null; then
    return 0
  fi
  printf '%s' "$KEYCLOAK_OIDC_CODE:$KEYCLOAK_OIDC_LOCATION" | grep -qiE '^(301|302|303|307|308):.*(login-actions|/realms/nebulaops|/keycloak/)'
}

keycloak_admin_token() {
  curl -fsS --max-time 10 \
    -X POST "${KEYCLOAK_DIRECT_URL}/realms/master/protocol/openid-connect/token" \
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
    -X PUT "${KEYCLOAK_DIRECT_URL}/admin/realms/nebulaops" \
    -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' \
    -d "{\"loginTheme\":\"$theme\",\"accountTheme\":\"keycloak\",\"adminTheme\":\"keycloak\",\"emailTheme\":\"keycloak\"}" >/dev/null
}

keycloak_logs_show_theme_error() {
  dc logs keycloak --tail=250 2>/dev/null | grep -qiE 'freemarker|template\.ftl|getTemplateForImporting|getTemplateForInclusion|Template\.java|InternalServerErrorException'
}

validate_keycloak_oidc_login() {
  log_step "Validating Keycloak OIDC login"
  wait_http "${KEYCLOAK_DIRECT_URL}/health/ready" 180 "keycloak" || {
    log_warn "Keycloak is not ready yet; run ./scripts/wsl/health.sh after startup"
    return 0
  }

  # Ensure the realm uses the NebulaOps theme when the theme is healthy.
  keycloak_set_login_theme "nebulaops" || true

  if keycloak_oidc_probe; then
    log_ok "Keycloak OIDC login page OK (HTTP 200)"
    return 0
  fi

  log_warn "Keycloak OIDC login returned HTTP ${KEYCLOAK_OIDC_CODE:-000}${KEYCLOAK_OIDC_LOCATION:+ Location=$KEYCLOAK_OIDC_LOCATION}"

  if [ "${KEYCLOAK_OIDC_CODE:-000}" = "500" ] || keycloak_logs_show_theme_error; then
    log_warn "Detected Keycloak FreeMarker/theme failure; applying built-in self-healing"
    write_custom_keycloak_login_theme
    dc restart keycloak >/dev/null
    wait_http "${KEYCLOAK_DIRECT_URL}/health/ready" 180 "keycloak after theme self-heal" || true
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


sso_redirect_probe() {
  local label="$1" url="$2" tmp code location
  tmp="$(mktemp)"
  code=$(curl -sS --max-time 10 -o /dev/null -D "$tmp" -w "%{http_code}" "$url" 2>/dev/null || true)
  location=$(awk 'BEGIN{IGNORECASE=1} /^location:/ {sub(/^[Ll]ocation:[[:space:]]*/, ""); gsub(/\r/, ""); print; exit}' "$tmp" 2>/dev/null || true)
  rm -f "$tmp"

  case "$code" in
    200|202)
      log_ok "$label SSO endpoint reachable ($url, HTTP $code)"
      return 0
      ;;
    301|302|303|307|308)
      if printf '%s' "$location" | grep -qiE '(/oauth2/(start|sign_in)|localhost:8180|keycloak)'; then
        log_ok "$label redirects to Keycloak/OAuth2 login ($url)"
        return 0
      fi
      ;;
  esac

  log_warn "$label SSO endpoint unexpected response: HTTP ${code:-000} Location=${location:-none} ($url)"
  return 1
}

validate_sso_proxy_redirects() {
  log_step "Validating SSO proxy redirects"
  sso_redirect_probe "RabbitMQ" "http://localhost:${RABBITMQ_SSO_HOST_PORT:-15673}/" || return 1
  sso_redirect_probe "Mongo Express" "http://localhost:${MONGO_EXPRESS_SSO_HOST_PORT:-18088}/" || return 1
  sso_redirect_probe "Redis Commander" "http://localhost:${REDIS_COMMANDER_SSO_HOST_PORT:-18089}/" || return 1
}


append_compose_extra_file() {
  local file="$1"
  if [ ! -f "$file" ]; then
    log_warn "Optional compose override missing: $file"
    return 0
  fi
  if [ -z "${NEBULAOPS_COMPOSE_EXTRA_FILE:-}" ]; then
    export NEBULAOPS_COMPOSE_EXTRA_FILE="$file"
  else
    case ":$NEBULAOPS_COMPOSE_EXTRA_FILE:" in
      *":$file:"*) ;;
      *) export NEBULAOPS_COMPOSE_EXTRA_FILE="$NEBULAOPS_COMPOSE_EXTRA_FILE:$file" ;;
    esac
  fi
}


port_owner_rows() {
  local port="$1"
  docker ps --filter "publish=$port" \
    --format '{{.ID}}|{{.Names}}|{{.Label "com.docker.compose.project"}}|{{.Label "com.docker.compose.service"}}' 2>/dev/null || true
}

release_nebulaops_port() {
  local port="$1" purpose="$2" rows row id name project service blocked=false
  rows="$(port_owner_rows "$port")"
  [ -z "$rows" ] && return 0

  while IFS='|' read -r id name project service; do
    [ -z "$id" ] && continue
    case "$project" in
      nebulaops|nebulaops-*|nebulaops_v*|nebulaops-v*)
        log_warn "Port $port is already used by previous NebulaOps container $name ($project/$service); removing it for $purpose"
        docker rm -f "$id" >/dev/null 2>&1 || true
        ;;
      "$PROJECT_NAME")
        log_warn "Port $port is already used by current NebulaOps container $name ($service); removing stale container for $purpose"
        docker rm -f "$id" >/dev/null 2>&1 || true
        ;;
      *)
        log_err "Port $port is already used by non-NebulaOps container '$name'. Stop it before starting $purpose."
        log_err "Inspect with: docker ps --filter publish=$port --format 'table {{.ID}}\t{{.Names}}\t{{.Ports}}'"
        blocked=true
        ;;
    esac
  done <<< "$rows"

  [ "$blocked" = "false" ] || exit 1
}

release_tool_ui_ports_for_mode() {
  local mode="$1"
  log_step "Checking tool UI ports for $mode mode"
  release_nebulaops_port "${GRAFANA_HOST_PORT:-3300}" "Grafana $mode UI"
  release_nebulaops_port 15672 "RabbitMQ native UI"
  release_nebulaops_port 8088  "Mongo Express native UI"
  release_nebulaops_port 8089  "Redis Commander native UI"
  if [ "$mode" = "SSO" ]; then
    release_nebulaops_port "${RABBITMQ_SSO_HOST_PORT:-15673}" "RabbitMQ SSO UI"
    release_nebulaops_port "${MONGO_EXPRESS_SSO_HOST_PORT:-18088}" "Mongo Express SSO UI"
    release_nebulaops_port "${REDIS_COMMANDER_SSO_HOST_PORT:-18089}" "Redis Commander SSO UI"
  fi
}

release_frontend_mfe_ports() {
  log_step "Checking shell and micro frontend ports"
  for port in ${NEBULAOPS_HTTP_PORT:-80} 4200 4211 4212 4213 4214 4215 4216 4217 4218 4219 4220 4221 4222 4223 4224 4225; do
    release_nebulaops_port "$port" "NebulaOps v22.4 frontend/micro frontend"
  done
}

release_v223_extended_service_ports() {
  log_step "Checking v22.4 extended service ports"
  release_nebulaops_port 8097 "NebulaOps v22.4 cost analytics service"
}

running_service() {
  dc ps --services --filter status=running 2>/dev/null | grep -qx "$1"
}

ensure_v223_extended_modules() {
  local services=(
    cost-analytics-service
    mfe-infra-hub
    mfe-release-center
    mfe-policy-center
    mfe-notification-center
    mfe-identity-admin
    mfe-progressive-delivery
  )

  log_step "Ensuring v22.4 extended modules"
  # These modules are part of the standard v22.4 cockpit and must be started
  # even when the shell is launched with optional SSO profiles. Running this
  # targeted up after the main compose up also repairs workspaces that were
  # started from an earlier v22.4 package where these endpoints were not active.
  dc up -d "${services[@]}"

  local service
  for service in "${services[@]}"; do
    if running_service "$service"; then
      log_ok "$service is running"
    else
      log_warn "$service is not running yet; inspect with: ./scripts/wsl/logs.sh $service"
    fi
  done

  wait_http "http://localhost:8097/actuator/health" 90 "cost-analytics-service" || \
    log_warn "Cost service is still warming up. Inspect: ./scripts/wsl/logs.sh cost-analytics-service"
  wait_http "${NEBULAOPS_PUBLIC_URL}/remotes/infra-hub/remoteEntry.js" 60 "mfe-infra-hub" || \
    log_warn "MFE INFRA is still warming up. Inspect: ./scripts/wsl/logs.sh mfe-infra-hub"
  wait_http "${NEBULAOPS_PUBLIC_URL}/remotes/release-center/remoteEntry.js" 60 "mfe-release-center" || \
    log_warn "MFE Release is still warming up. Inspect: ./scripts/wsl/logs.sh mfe-release-center"
  wait_http "${NEBULAOPS_PUBLIC_URL}/remotes/policy-center/remoteEntry.js" 60 "mfe-policy-center" || \
    log_warn "MFE Policy is still warming up. Inspect: ./scripts/wsl/logs.sh mfe-policy-center"
  wait_http "${NEBULAOPS_PUBLIC_URL}/remotes/notification-center/remoteEntry.js" 60 "mfe-notification-center" || \
    log_warn "MFE Notifications is still warming up. Inspect: ./scripts/wsl/logs.sh mfe-notification-center"
  wait_http "${NEBULAOPS_PUBLIC_URL}/remotes/identity-admin/remoteEntry.js" 60 "mfe-identity-admin" || \
    log_warn "MFE Identity Admin is still warming up. Inspect: ./scripts/wsl/logs.sh mfe-identity-admin"
  wait_http "${NEBULAOPS_PUBLIC_URL}/remotes/progressive-delivery/remoteEntry.js" 60 "mfe-progressive-delivery" || \
    log_warn "MFE Progressive Delivery is still warming up. Inspect: ./scripts/wsl/logs.sh mfe-progressive-delivery"
  wait_http "http://localhost:8102/actuator/health" 90 "progressive-delivery-service" || \
    log_warn "Progressive Delivery service is still warming up. Inspect: ./scripts/wsl/logs.sh progressive-delivery-service"
}

served_mfe_remote_is_classic() {
  local slug="$1" body
  body="$(curl -fsS --max-time 5 "${NEBULAOPS_PUBLIC_URL}/remotes/${slug}/remoteEntry.js" 2>/dev/null || true)"
  [ -n "$body" ] || return 1
  if printf '%s' "$body" | grep -Eq '\bexport[[:space:]]+(default|\{|class|function|const|let|var)'; then
    return 1
  fi
  if ! printf '%s' "$body" | grep -Eq 'NebulaOps v22.4 auth bridge|nebulaopsAuthBridge'; then
    return 1
  fi
  printf '%s' "$body" | grep -Eq 'customElements\.define|classic standalone custom element'
}

ensure_v223_live_mfe_remote_entries() {
  local remotes=(docker-desktop openlens-kubernetes task-management observability cicd-gitops terraform-studio devsecops ai-ops finops-cost infra-hub release-center policy-center notification-center identity-admin progressive-delivery)
  local slug invalid=0
  log_step "Verifying live MFE runtime bundles as same-origin static bundles"
  for slug in "${remotes[@]}"; do
    if served_mfe_remote_is_classic "$slug"; then
      log_ok "MFE ${slug} remoteEntry.js is shell-compatible"
    else
      log_warn "MFE ${slug} is missing, stale, or still serving ESM syntax"
      invalid=1
    fi
  done

  if [ "$invalid" -ne 0 ]; then
    log_warn "Blank MFE body risk detected. Run ./scripts/wsl/repair-v22.4-frontend-remotes.sh if the browser still shows empty MFE pages."
    if [ "${NEBULAOPS_AUTO_REPAIR_MFE:-false}" = "true" ]; then
      "$ROOT_DIR/scripts/wsl/repair-v22.4-frontend-remotes.sh"
    fi
  fi
}

log_step "Pre-flight checks"
"$ROOT_DIR/scripts/wsl/check-wsl.sh"

log_step "Preparing kubeconfig and runtime tools"
"$ROOT_DIR/scripts/wsl/prepare-kubeconfig-for-docker.sh"
"$ROOT_DIR/scripts/wsl/prepare-runtime-tools.sh"

log_step "Preparing Keycloak login theme"
prepare_keycloak_theme_before_start

release_frontend_mfe_ports
release_v223_extended_service_ports

log_step "Validating frontend runtime artifacts"
"$ROOT_DIR/scripts/wsl/ensure-frontend-dist.sh"

log_step "Validating Grafana provisioning"
defaults=$(grep -R "isDefault: true" -n \
  infrastructure/observability/grafana/provisioning/datasources 2>/dev/null | wc -l || true)
if [ "$defaults" -ne 1 ]; then
  log_err "Grafana must have exactly one isDefault datasource (found $defaults)"
  exit 1
fi
log_ok "Grafana datasource provisioning OK"

log_step "Ensuring backend JARs"
if [ "$REBUILD_ALL" = "true" ]; then
  "$ROOT_DIR/scripts/wsl/build-backend-jars.sh" --force
else
  "$ROOT_DIR/scripts/wsl/build-backend-jars.sh"
fi

# Root docker-compose builds backend runtime images from context '.'.
# The backend target/*.jar files must remain visible in Docker build context.
"$ROOT_DIR/scripts/wsl/repair-v22.4-docker-context.sh"

if [ "$REBUILD_GATEWAY" = "true" ]; then
  log_step "Force-rebuilding gateway-service"
  dc build gateway-service
fi

log_step "Starting NebulaOps v22.4"
# Ensure shared Docker network exists (external: true in docker-compose.yml)
if ! docker network inspect nebulaops-network &>/dev/null; then
  log_info "Creating shared network: nebulaops-network"
  docker network create nebulaops-network
else
  log_info "Network nebulaops-network already exists"
fi
export COMPOSE_PARALLEL_LIMIT="${COMPOSE_PARALLEL_LIMIT:-2}"
compose_profiles=()
if [ -n "${COMPOSE_PROFILES:-}" ]; then
  IFS=',' read -r -a compose_profiles <<< "$COMPOSE_PROFILES"
fi
if [ "$WITH_GITLAB" = "true" ]; then
  log_warn "Starting optional GitLab CE profile. This is heavy and may take several minutes."
  compose_profiles+=("gitlab")
else
  log_info "GitLab CE is disabled by default. Use ./scripts/wsl/start.sh --with-gitlab only when needed."
fi
# Prometheus remains available on localhost:9090 in both native and SSO tool modes.
append_compose_extra_file "$ROOT_DIR/docker-compose.prometheus-ui.yml"

if [ "$WITH_SSO_PROXY" = "true" ]; then
  log_warn "Starting optional SSO proxy profile for RabbitMQ, Mongo Express and Redis Commander."
  log_info "Prometheus remains native on http://localhost:9090 to avoid mixing observability health with tool SSO wrappers."
  log_step "Checking OAuth2 Proxy images"
  "$ROOT_DIR/scripts/oauth2-proxy-preflight.sh"
  log_step "Recreating SSO proxy bridge containers"
  dc rm -sf rabbitmq-basic-proxy mongo-express-basic-proxy redis-commander-basic-proxy rabbitmq-management-sso mongo-express-sso redis-commander-sso >/dev/null 2>&1 || true
  release_tool_ui_ports_for_mode "SSO"
  compose_profiles+=("sso-proxy")
else
  log_info "SSO proxy wrappers are disabled by default. Native tool UIs are exposed by docker-compose.native-ui.yml."
  release_tool_ui_ports_for_mode "native"
  append_compose_extra_file "$ROOT_DIR/docker-compose.native-ui.yml"
fi
if [ "${#compose_profiles[@]}" -gt 0 ]; then
  export COMPOSE_PROFILES="$(IFS=','; echo "${compose_profiles[*]}")"
fi
if [ "$REBUILD_ALL" = "true" ]; then
  log_step "Building Docker images"
  dc build
fi
dc up -d --remove-orphans
ensure_v223_extended_modules
ensure_v223_live_mfe_remote_entries

log_step "Ensuring Keycloak clients"
"$ROOT_DIR/scripts/keycloak-ensure-sso-clients.sh" || log_warn "Keycloak client auto-check failed; use ./scripts/keycloak-ensure-sso-clients.sh after startup"

validate_keycloak_oidc_login || log_warn "Keycloak custom login is not healthy yet; run ./scripts/wsl/health.sh and inspect keycloak logs"
if [ "$WITH_SSO_PROXY" = "true" ]; then
  validate_sso_proxy_redirects || log_warn "SSO proxy redirects are not all ready yet; run ./scripts/wsl/health.sh and inspect *-sso logs"
fi

log_step "Waiting for gateway-service"
wait_http "http://localhost:8080/actuator/health" 120 "gateway-service" || \
  log_warn "Inspect logs: ./scripts/wsl/logs.sh gateway-service"

cat <<INFO

${C_BOLD}NebulaOps v22.4 is running.${C_RESET}

  ${C_CYAN}Frontend${C_RESET}    ${NEBULAOPS_PUBLIC_URL}
  ${C_CYAN}Gateway${C_RESET}     ${NEBULAOPS_PUBLIC_URL}/actuator/health
  ${C_CYAN}Grafana${C_RESET}     ${NEBULAOPS_PUBLIC_URL}/grafana/        admin/admin
  ${C_CYAN}Keycloak${C_RESET}    ${KEYCLOAK_PUBLIC_URL}        admin/admin
  ${C_CYAN}GitLab${C_RESET}      optional: ./scripts/wsl/start.sh --with-gitlab
  ${C_CYAN}SSO Proxies${C_RESET} optional: ./scripts/wsl/start.sh --with-sso-proxy
  ${C_CYAN}Prometheus${C_RESET}  ${NEBULAOPS_PUBLIC_URL}/prometheus/        native health/UI in every mode
  ${C_CYAN}RabbitMQ${C_RESET}    native http://localhost:15672       guest/guest; SSO http://localhost:${RABBITMQ_SSO_HOST_PORT:-15673}
  ${C_CYAN}Mongo${C_RESET}       native http://localhost:8088        admin/admin; SSO http://localhost:${MONGO_EXPRESS_SSO_HOST_PORT:-18088}
  ${C_CYAN}Redis UI${C_RESET}    native http://localhost:8089        admin/admin; SSO http://localhost:${REDIS_COMMANDER_SSO_HOST_PORT:-18089}

Useful:  ./scripts/wsl/health.sh        — overall status
         ./scripts/wsl/logs.sh <svc>    — tail service logs
         ./scripts/wsl/restart-gateway.sh — quick gateway restart
         ./scripts/wsl/stop.sh          — shut everything down

INFO

command -v explorer.exe >/dev/null 2>&1 && \
  explorer.exe "$NEBULAOPS_PUBLIC_URL" >/dev/null 2>&1 || true
