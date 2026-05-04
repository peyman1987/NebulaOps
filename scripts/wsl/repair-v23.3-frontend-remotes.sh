#!/usr/bin/env bash
# NebulaOps v23.3 frontend/MFE runtime repair.
# Reuses valid packaged Angular/custom-element dist artifacts when available, rebuilds nginx runtime
# images with Docker layer cache by default, recreates containers, and verifies that the shell serves
# classic same-origin remoteEntry.js bundles for every MFE.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

services=(
  frontend
  mfe-platform-catalog
  mfe-incident-command-center
  mfe-runtime-readiness
  mfe-docker-storage-cleanup
  mfe-environment-configuration
  mfe-dependency-impact
  mfe-test-quality-dashboard
  mfe-docker-desktop
  mfe-openlens-kubernetes
  mfe-task-management
  mfe-observability
  mfe-cicd-gitops
  mfe-terraform-studio
  mfe-devsecops
  mfe-ai-ops
  mfe-finops-cost
  mfe-infra-hub
  mfe-release-center
  mfe-policy-center
  mfe-notification-center
  mfe-identity-admin
  mfe-progressive-delivery
)

remote_slugs=(
  platform-catalog
  incident-command-center
  runtime-readiness
  docker-storage-cleanup
  environment-configuration
  dependency-impact
  test-quality-dashboard
  docker-desktop
  openlens-kubernetes
  task-management
  observability
  cicd-gitops
  terraform-studio
  devsecops
  ai-ops
  finops-cost
  infra-hub
  release-center
  policy-center
  notification-center
  identity-admin
  progressive-delivery
)

image_for_service() {
  local svc="$1"
  if [ "$svc" = "frontend" ]; then
    printf 'nebulaops-v23-3-frontend-shell:latest\n'
  else
    printf 'nebulaops-v23-3-%s:latest\n' "$svc"
  fi
}


is_frontend_mfe_service_name() {
  local svc="$1"
  case "$svc" in
    frontend|mfe-*) return 0 ;;
    *) return 1 ;;
  esac
}

remove_stale_frontend_mfe_containers_only() {
  local rows id name project service
  rows="$(docker ps -aq --filter "name=nebulaops" | xargs -r docker inspect \
    --format '{{.Id}}|{{.Name}}|{{ index .Config.Labels "com.docker.compose.project" }}|{{ index .Config.Labels "com.docker.compose.service" }}' 2>/dev/null || true)"
  [ -z "$rows" ] && return 0
  while IFS='|' read -r id name project service; do
    [ -z "$id" ] && continue
    if is_frontend_mfe_service_name "${service:-}"; then
      log_warn "Removing stale frontend/MFE container ${name#/} (${project:-unknown}/${service:-unknown})"
      docker rm -f "$id" >/dev/null 2>&1 || true
    fi
  done <<< "$rows"
}


frontend_container_id() {
  dc ps -q frontend 2>/dev/null | head -n 1
}

sync_frontend_runtime_container_from_local_dist() {
  local cid slug shell_dist remote_dist
  cid="$(frontend_container_id)"
  if [ -z "$cid" ]; then
    log_err "Cannot sync frontend runtime: frontend container is not running"
    return 1
  fi
  shell_dist="$ROOT_DIR/frontend/dist/nebulaops/browser"
  if [ ! -d "$shell_dist" ]; then
    log_err "Cannot sync frontend shell dist: missing $shell_dist"
    return 1
  fi

  log_step "Syncing local shell/MFE dist directly into the running frontend container"
  # This is intentionally done after Docker image rebuild and container recreate.
  # It removes the last possible stale layer: if Docker/WSL served an older copied
  # remoteEntry.js despite a successful image build, the runtime container is made
  # authoritative from the already-validated local dist artifacts.
  docker exec "$cid" sh -lc 'rm -rf /usr/share/nginx/html/* && mkdir -p /usr/share/nginx/html/remotes' >/dev/null
  docker cp "$shell_dist/." "$cid:/usr/share/nginx/html/" >/dev/null
  docker exec "$cid" sh -lc 'mkdir -p /usr/share/nginx/html/remotes' >/dev/null

  for slug in "${remote_slugs[@]}"; do
    remote_dist="$ROOT_DIR/frontend/remotes/${slug}/dist/browser"
    if [ ! -f "$remote_dist/remoteEntry.js" ]; then
      log_err "Cannot sync MFE ${slug}: missing $remote_dist/remoteEntry.js"
      return 1
    fi
    docker exec "$cid" sh -lc "rm -rf /usr/share/nginx/html/remotes/${slug} && mkdir -p /usr/share/nginx/html/remotes/${slug}" >/dev/null
    docker cp "$remote_dist/." "$cid:/usr/share/nginx/html/remotes/${slug}/" >/dev/null
  done

  # Also overwrite the Nginx config at runtime. Older containers/images used a
  # proxy-based /remotes/* config, so copying only the dist files was not enough:
  # public /remotes/<slug>/remoteEntry.js could still be served from stale MFE
  # containers even after the frontend HTML tree had been synced.
  # Remove every old conf.d file before installing the v23.3 config. Some previous
  # packages left a second server/location file that proxied /remotes/* to stale
  # MFE containers; overwriting only default.conf was not enough.
  docker exec "$cid" sh -lc 'mkdir -p /tmp/nebulaops-nginx-conf-backup && cp -a /etc/nginx/conf.d/. /tmp/nebulaops-nginx-conf-backup/ 2>/dev/null || true && rm -f /etc/nginx/conf.d/*.conf' >/dev/null
  docker cp "$ROOT_DIR/frontend/nginx.conf" "$cid:/etc/nginx/conf.d/default.conf" >/dev/null
  docker exec "$cid" sh -lc 'nginx -t >/dev/null 2>&1 && nginx -s reload >/dev/null 2>&1 || true; find /var/cache/nginx -type f -delete 2>/dev/null || true' >/dev/null
  log_ok "Running frontend container has been synced from local v23.3 dist artifacts"
}

mfe_container_id() {
  local slug="$1" svc="mfe-${slug}"
  dc ps -q "$svc" 2>/dev/null | head -n 1
}

sync_mfe_runtime_containers_from_local_dist() {
  local slug svc cid remote_dist nginx_conf
  log_step "Syncing individual MFE runtime containers from local dist"
  for slug in "${remote_slugs[@]}"; do
    svc="mfe-${slug}"
    cid="$(mfe_container_id "$slug")"
    if [ -z "$cid" ]; then
      log_warn "Skipping ${svc}: container is not running"
      continue
    fi
    remote_dist="$ROOT_DIR/frontend/remotes/${slug}/dist/browser"
    nginx_conf="$ROOT_DIR/frontend/remotes/${slug}/nginx.conf"
    [ -f "$remote_dist/remoteEntry.js" ] || { log_err "Cannot sync ${svc}: missing $remote_dist/remoteEntry.js"; return 1; }
    [ -f "$nginx_conf" ] || { log_err "Cannot sync ${svc}: missing $nginx_conf"; return 1; }
    docker exec "$cid" sh -lc 'rm -rf /usr/share/nginx/html/*' >/dev/null
    docker cp "$remote_dist/." "$cid:/usr/share/nginx/html/" >/dev/null
    docker cp "$nginx_conf" "$cid:/etc/nginx/conf.d/default.conf" >/dev/null
    docker exec "$cid" sh -lc 'nginx -t >/dev/null 2>&1 && nginx -s reload >/dev/null 2>&1 || true; find /var/cache/nginx -type f -delete 2>/dev/null || true' >/dev/null
  done
  log_ok "All running MFE containers have been synced from local v23.3 dist artifacts"
}

assert_mfe_container_remote_matches_local() {
  local slug="$1" cid local_file container_sha local_sha
  cid="$(mfe_container_id "$slug")"
  [ -n "$cid" ] || return 0
  local_file="$ROOT_DIR/frontend/remotes/${slug}/dist/browser/remoteEntry.js"
  container_sha="$(docker exec "$cid" sh -lc "sha256sum /usr/share/nginx/html/remoteEntry.js 2>/dev/null | awk '{print \$1}'" 2>/dev/null || true)"
  local_sha="$(sha256sum "$local_file" | awk '{print $1}')"
  if [ -z "$container_sha" ] || [ "$container_sha" != "$local_sha" ]; then
    log_err "MFE container remoteEntry.js differs from local dist for ${slug}"
    log_info "local sha=${local_sha:-missing} container sha=${container_sha:-missing}"
    return 1
  fi
}

write_frontend_runtime_marker() {
  local cid marker
  cid="$(frontend_container_id)"
  [ -n "$cid" ] || return 1
  marker="nebulaops-v23.3-frontend-${cid}-$(date +%s)"
  printf '%s' "$marker" >"$ROOT_DIR/.nebulaops-runtime-owner.expected"
  docker exec "$cid" sh -lc "printf '%s' '$marker' >/usr/share/nginx/html/__nebulaops-runtime-owner.txt" >/dev/null
}

assert_public_url_targets_frontend_container() {
  local expected actual url
  expected="$(cat "$ROOT_DIR/.nebulaops-runtime-owner.expected" 2>/dev/null || true)"
  [ -n "$expected" ] || return 0
  while IFS= read -r url; do
    [ -z "$url" ] && continue
    actual="$(curl -fsS --max-time 5 -H 'Cache-Control: no-cache' -H 'Pragma: no-cache' "${url%/}/__nebulaops-runtime-owner.txt?v=$(date +%s)" 2>/dev/null || true)"
    if [ "$actual" = "$expected" ]; then
      log_ok "${url%/} is served by the current frontend container"
      return 0
    fi
  done < <(shell_probe_urls)
  log_err "${NEBULAOPS_PUBLIC_URL} is not serving the current frontend container content."
  log_info "Expected runtime marker: $expected"
  log_info "Published port owners:"
  docker ps --filter "publish=${NEBULAOPS_HTTP_PORT:-80}" --format '  {{.ID}}  {{.Names}}  {{.Ports}}' || true
  log_info "All NebulaOps frontend containers:"
  docker ps -a --filter "label=com.docker.compose.service=frontend" --format '  {{.ID}}  {{.Names}}  {{.Status}}  {{.Ports}}' || true
  return 1
}

assert_container_remote_matches_local() {
  local slug="$1" cid local_file container_sha local_sha
  cid="$(frontend_container_id)"
  local_file="$ROOT_DIR/frontend/remotes/${slug}/dist/browser/remoteEntry.js"
  [ -n "$cid" ] || { log_err "frontend container is not running"; return 1; }
  [ -f "$local_file" ] || { log_err "Missing local dist bundle: $local_file"; return 1; }
  container_sha="$(docker exec "$cid" sh -lc "sha256sum /usr/share/nginx/html/remotes/${slug}/remoteEntry.js 2>/dev/null | awk '{print \$1}'" 2>/dev/null || true)"
  local_sha="$(sha256sum "$local_file" | awk '{print $1}')"
  if [ -z "$container_sha" ] || [ "$container_sha" != "$local_sha" ]; then
    log_err "Frontend container remoteEntry.js differs from local dist for ${slug}"
    log_info "local sha=${local_sha:-missing} container sha=${container_sha:-missing}"
    return 1
  fi
}

shell_probe_urls() {
  printf '%s\n' "${NEBULAOPS_PUBLIC_URL%/}/"
  if [ "${NEBULAOPS_PUBLIC_URL%/}" != "http://localhost:${NEBULAOPS_HTTP_PORT:-80}" ]; then
    if [ "${NEBULAOPS_HTTP_PORT:-80}" = "80" ]; then
      printf '%s\n' "http://localhost/"
    else
      printf '%s\n' "http://localhost:${NEBULAOPS_HTTP_PORT:-80}/"
    fi
  fi
}

wait_frontend_shell() {
  local timeout="${1:-180}" url i
  for ((i=1; i<=timeout; i++)); do
    while IFS= read -r url; do
      [ -z "$url" ] && continue
      if curl -fsS --max-time 3 "$url" >/dev/null 2>&1; then
        log_ok "frontend shell is reachable ($url)"
        return 0
      fi
    done < <(shell_probe_urls)
    sleep 1
  done
  log_warn "frontend shell not ready after ${timeout}s (${NEBULAOPS_PUBLIC_URL}/)"
  return 1
}

frontend_shell_diagnostics() {
  log_warn "Frontend shell diagnostics"
  log_info "Published port owners:"
  docker ps --filter "publish=${NEBULAOPS_HTTP_PORT:-80}" --format '  {{.ID}}  {{.Names}}  {{.Ports}}' || true
  log_info "Compose frontend status:"
  dc ps frontend || true
  log_info "Frontend container logs:"
  dc logs frontend --tail=160 || true
  log_info "Nginx/container internal probe:"
  dc exec -T frontend sh -lc 'nginx -t 2>&1 || true; echo --- html ---; ls -la /usr/share/nginx/html | head -40; echo --- local root ---; wget -S -O - http://127.0.0.1:80/ 2>&1 | head -80' || true
  log_info "Host probes:"
  for url in $(shell_probe_urls); do
    printf '  %s -> ' "$url"
    curl -sS --max-time 5 -o /dev/null -w 'HTTP %{http_code} remote=%{remote_ip}\n' "$url" 2>&1 || true
  done
}

fetch_remote_entry_body() {
  local slug="$1" base body
  while IFS= read -r base; do
    [ -z "$base" ] && continue
    body="$(curl -fsS --max-time 8 -H 'Cache-Control: no-cache' -H 'Pragma: no-cache' "${base%/}/remotes/${slug}/remoteEntry.js?v=$(date +%s)" 2>/dev/null || true)"
    if [ -n "$body" ]; then
      printf '%s' "$body"
      return 0
    fi
  done < <(shell_probe_urls)
  return 1
}

fetch_auth_bridge_body() {
  local slug="$1" base body
  while IFS= read -r base; do
    [ -z "$base" ] && continue
    body="$(curl -fsS --max-time 8 -H 'Cache-Control: no-cache' -H 'Pragma: no-cache' "${base%/}/remotes/${slug}/nebulaops-auth-bridge.js?v=$(date +%s)" 2>/dev/null || true)"
    if printf '%s' "$body" | grep -Eq 'NebulaOps v23.3 auth bridge|nebulaopsAuthBridge'; then
      printf '%s' "$body"
      return 0
    fi
    body="$(curl -fsS --max-time 8 -H 'Cache-Control: no-cache' -H 'Pragma: no-cache' "${base%/}/assets/nebulaops-auth-bridge.js?v=$(date +%s)" 2>/dev/null || true)"
    if printf '%s' "$body" | grep -Eq 'NebulaOps v23.3 auth bridge|nebulaopsAuthBridge'; then
      printf '%s' "$body"
      return 0
    fi
  done < <(shell_probe_urls)
  return 1
}

assert_local_remote_is_classic() {
  local slug="$1" file="frontend/remotes/${slug}/dist/browser/remoteEntry.js"
  if [ ! -f "$file" ]; then
    log_err "Missing local dist bundle: $file"
    return 1
  fi
  if grep -Eq '\bexport[[:space:]]+(default|\{|class|function|const|let|var)' "$file"; then
    log_err "Invalid ESM syntax in local bundle: $file"
    return 1
  fi
  if ! grep -Eq 'NebulaOps v23.3 auth bridge|nebulaopsAuthBridge' "$file"; then
    if ! grep -Eq 'NebulaOps v23.3 auth bridge|nebulaopsAuthBridge' "frontend/remotes/${slug}/dist/browser/nebulaops-auth-bridge.js" 2>/dev/null; then
      log_err "Missing auth bridge in local bundle and colocated bridge asset for $slug"
      return 1
    fi
    log_warn "MFE ${slug} remoteEntry.js has no inline auth bridge, but the colocated auth bridge asset is present. Continuing."
  fi
  if ! grep -Eq 'customElements\.define|classic standalone custom element' "$file"; then
    log_err "Local bundle is not a shell-compatible custom element: $file"
    return 1
  fi
}

assert_served_remote_is_classic() {
  local slug="$1" body local_file served_sha local_sha
  local_file="frontend/remotes/${slug}/dist/browser/remoteEntry.js"
  body="$(fetch_remote_entry_body "$slug" || true)"
  if [ -z "$body" ]; then
    log_err "MFE ${slug} did not serve remoteEntry.js from the frontend shell"
    return 1
  fi
  if command -v sha256sum >/dev/null 2>&1 && [ -f "$local_file" ]; then
    served_sha="$(printf '%s' "$body" | sha256sum | awk '{print $1}')"
    local_sha="$(sha256sum "$local_file" | awk '{print $1}')"
    if [ "$served_sha" != "$local_sha" ]; then
      log_warn "MFE ${slug} served remoteEntry.js differs from local dist; frontend image/container is stale."
    fi
  fi
  if printf '%s' "$body" | grep -Eq '\bexport[[:space:]]+(default|\{|class|function|const|let|var)'; then
    log_err "MFE ${slug} is still serving an ESM remoteEntry.js. Browser will fail with: Unexpected token 'export'."
    return 1
  fi
  if ! printf '%s' "$body" | grep -Eq 'customElements\.define|classic standalone custom element'; then
    log_err "MFE ${slug} remoteEntry.js is not a shell-compatible custom element bundle."
    return 1
  fi
  if printf '%s' "$body" | grep -Eq 'NebulaOps v23.3 auth bridge|nebulaopsAuthBridge'; then
    log_ok "MFE ${slug} serves classic shell-compatible remoteEntry.js with inline auth bridge"
    return 0
  fi
  local bridge
  bridge="$(fetch_auth_bridge_body "$slug" || true)"
  if [ -n "$bridge" ]; then
    log_ok "MFE ${slug} serves classic shell-compatible remoteEntry.js; auth bridge is served as a separate asset"
    return 0
  fi
  log_err "MFE ${slug} remoteEntry.js is shell-compatible, but no auth bridge asset is reachable."
  return 1
}

log_step "Checking local frontend shell and MFE dist artifacts"
if [ "${NEBULAOPS_SKIP_FRONTEND_DIST_BUILD:-false}" = "true" ]; then
  log_warn "Skipping local frontend dist build because NEBULAOPS_SKIP_FRONTEND_DIST_BUILD=true"
else
  "$ROOT_DIR/scripts/wsl/build-frontend-local.sh"
fi

log_step "Validating local shell-compatible MFE artifacts"
"$ROOT_DIR/scripts/wsl/ensure-frontend-dist.sh"
for slug in "${remote_slugs[@]}"; do
  assert_local_remote_is_classic "$slug"
done

log_step "Removing stale frontend/MFE containers"
dc rm -sf "${services[@]}" >/dev/null 2>&1 || true
# Remove only frontend/MFE containers left by older NebulaOps compose project names.
# Do not remove backend, Keycloak, Grafana, Prometheus or data services here: killing
# the whole stack during a frontend repair creates startup races and can make Nginx
# fail to resolve upstream service names while the shell is being verified.
remove_stale_frontend_mfe_containers_only

log_step "Removing stale frontend/MFE images"
for svc in "${services[@]}"; do
  docker image rm -f "$(image_for_service "$svc")" >/dev/null 2>&1 || true
done

# Default to no-cache for the frontend/MFE repair path. The bug this script fixes
# is exactly stale remoteEntry.js being served from an old shell/MFE image; saving
# a few seconds with Docker cache is not worth risking a stale auth bridge.
if [ "${NEBULAOPS_FORCE_NO_CACHE_MFE_IMAGES:-true}" = "true" ]; then
  log_step "Rebuilding frontend/MFE runtime images without Docker cache"
  dc build --no-cache --pull "${services[@]}"
else
  log_step "Rebuilding frontend/MFE runtime images with Docker layer cache"
  dc build "${services[@]}"
fi

log_step "Starting frontend/MFE containers from rebuilt images"
dc up -d --force-recreate --remove-orphans "${services[@]}"

sync_frontend_runtime_container_from_local_dist
sync_mfe_runtime_containers_from_local_dist
write_frontend_runtime_marker

log_step "Checking same-origin MFE remoteEntry endpoints"
if ! wait_frontend_shell 180; then
  frontend_shell_diagnostics
  log_err "Frontend shell is not reachable from WSL. Fix the container/port issue above, then rerun: ./scripts/wsl/repair-v23.3-frontend-remotes.sh"
  exit 1
fi
assert_public_url_targets_frontend_container || {
  log_err "The public host is not connected to the rebuilt frontend container. Stop any other process/container on port ${NEBULAOPS_HTTP_PORT:-80}, then rerun: ./scripts/wsl/repair-v23.3-frontend-remotes.sh"
  exit 1
}
failed=0
for slug in "${remote_slugs[@]}"; do
  wait_http "${NEBULAOPS_PUBLIC_URL}/remotes/${slug}/remoteEntry.js" 90 "MFE ${slug}" || failed=1
  assert_container_remote_matches_local "$slug" || failed=1
  assert_mfe_container_remote_matches_local "$slug" || failed=1
  assert_served_remote_is_classic "$slug" || failed=1
done

if [ "$failed" -ne 0 ]; then
  log_err "One or more MFE runtime bundles are still invalid."
  log_info "Active frontend Nginx /remotes location:"
  cid="$(frontend_container_id)"
  [ -n "$cid" ] && docker exec "$cid" sh -lc "nginx -T 2>/dev/null | sed -n '/location \^~ \/remotes\//,/}/p' | head -60" || true
  log_info "Port owners:"
  docker ps --format '  {{.ID}}  {{.Names}}  {{.Ports}}' | grep -E '(:80->|0.0.0.0:80|:::80)' || true
  log_err "Inspect with: curl ${NEBULAOPS_PUBLIC_URL}/remotes/<slug>/remoteEntry.js | head"
  exit 1
fi

log_ok "All NebulaOps v23.3 MFE runtime bundles are rebuilt and shell-compatible"
log_info "Open ${NEBULAOPS_PUBLIC_URL}/?v=v23.3.0-live-real-data and press Ctrl+Shift+R. If Chrome still shows old JavaScript, clear site data for nebulaops.localhost."
