#!/usr/bin/env bash
# NebulaOps v23.2 runtime overlay sync.
# Copies the already-validated local shell/MFE dist artifacts into the running frontend
# container and verifies the same-origin remoteEntry.js endpoints. This bypasses stale
# Docker layer/context issues without rebuilding Angular again.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

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

frontend_container_id() {
  dc ps -q frontend 2>/dev/null | head -n 1
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
    # Standalone MFE route first, then shell asset. Both are valid: the shell
    # loads the global bridge, while standalone remotes load their colocated bridge.
    body="$(curl -fsS --max-time 8 -H 'Cache-Control: no-cache' -H 'Pragma: no-cache' "${base%/}/remotes/${slug}/nebulaops-auth-bridge.js?v=$(date +%s)" 2>/dev/null || true)"
    if printf '%s' "$body" | grep -Eq 'NebulaOps v23.2 auth bridge|nebulaopsAuthBridge'; then
      printf '%s' "$body"
      return 0
    fi
    body="$(curl -fsS --max-time 8 -H 'Cache-Control: no-cache' -H 'Pragma: no-cache' "${base%/}/assets/nebulaops-auth-bridge.js?v=$(date +%s)" 2>/dev/null || true)"
    if printf '%s' "$body" | grep -Eq 'NebulaOps v23.2 auth bridge|nebulaopsAuthBridge'; then
      printf '%s' "$body"
      return 0
    fi
  done < <(shell_probe_urls)
  return 1
}

assert_local_remote_is_classic() {
  local slug="$1" file="$ROOT_DIR/frontend/remotes/${slug}/dist/browser/remoteEntry.js"
  [ -f "$file" ] || { log_err "Missing local dist bundle: $file"; return 1; }
  if grep -Eq '\bexport[[:space:]]+(default|\{|class|function|const|let|var)' "$file"; then
    log_err "Invalid ESM syntax in local bundle: $file"
    return 1
  fi
  if ! grep -Eq 'NebulaOps v23.2 auth bridge|nebulaopsAuthBridge' "$file"; then
    if ! grep -Eq 'NebulaOps v23.2 auth bridge|nebulaopsAuthBridge' "$ROOT_DIR/frontend/remotes/${slug}/dist/browser/nebulaops-auth-bridge.js" 2>/dev/null; then
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

sync_frontend_runtime_container_from_local_dist() {
  local cid slug shell_dist remote_dist
  cid="$(frontend_container_id)"
  if [ -z "$cid" ]; then
    log_err "Frontend container is not running. Start it first with: ./scripts/wsl/start.sh --skip-preflight"
    return 1
  fi
  shell_dist="$ROOT_DIR/frontend/dist/nebulaops/browser"
  [ -d "$shell_dist" ] || { log_err "Missing shell dist: $shell_dist"; return 1; }

  for slug in "${remote_slugs[@]}"; do
    assert_local_remote_is_classic "$slug"
  done

  log_step "Copying local shell dist into frontend container"
  docker exec "$cid" sh -lc 'rm -rf /usr/share/nginx/html/* && mkdir -p /usr/share/nginx/html/remotes' >/dev/null
  docker cp "$shell_dist/." "$cid:/usr/share/nginx/html/" >/dev/null
  docker exec "$cid" sh -lc 'mkdir -p /usr/share/nginx/html/remotes' >/dev/null

  log_step "Copying local MFE dist artifacts into frontend container"
  for slug in "${remote_slugs[@]}"; do
    remote_dist="$ROOT_DIR/frontend/remotes/${slug}/dist/browser"
    docker exec "$cid" sh -lc "rm -rf /usr/share/nginx/html/remotes/${slug} && mkdir -p /usr/share/nginx/html/remotes/${slug}" >/dev/null
    docker cp "$remote_dist/." "$cid:/usr/share/nginx/html/remotes/${slug}/" >/dev/null
  done

  # Remove every old conf.d file before installing the v23.2 config. Some previous
  # packages left a second server/location file that proxied /remotes/* to stale
  # MFE containers; overwriting only default.conf was not enough.
  docker exec "$cid" sh -lc 'mkdir -p /tmp/nebulaops-nginx-conf-backup && cp -a /etc/nginx/conf.d/. /tmp/nebulaops-nginx-conf-backup/ 2>/dev/null || true && rm -f /etc/nginx/conf.d/*.conf' >/dev/null
  docker cp "$ROOT_DIR/frontend/nginx.conf" "$cid:/etc/nginx/conf.d/default.conf" >/dev/null
  docker exec "$cid" sh -lc 'nginx -t >/dev/null 2>&1 && nginx -s reload >/dev/null 2>&1 || true; find /var/cache/nginx -type f -delete 2>/dev/null || true' >/dev/null
  log_ok "Frontend container content is now synced from local v23.2 dist artifacts"
}

mfe_container_id() {
  local slug="$1"
  dc ps -q "mfe-${slug}" 2>/dev/null | head -n 1
}

sync_mfe_runtime_containers_from_local_dist() {
  local slug cid remote_dist nginx_conf
  log_step "Syncing individual MFE runtime containers from local dist"
  for slug in "${remote_slugs[@]}"; do
    cid="$(mfe_container_id "$slug")"
    [ -z "$cid" ] && continue
    remote_dist="$ROOT_DIR/frontend/remotes/${slug}/dist/browser"
    nginx_conf="$ROOT_DIR/frontend/remotes/${slug}/nginx.conf"
    [ -f "$remote_dist/remoteEntry.js" ] || { log_err "Missing $remote_dist/remoteEntry.js"; return 1; }
    [ -f "$nginx_conf" ] || { log_err "Missing $nginx_conf"; return 1; }
    docker exec "$cid" sh -lc 'rm -rf /usr/share/nginx/html/*' >/dev/null
    docker cp "$remote_dist/." "$cid:/usr/share/nginx/html/" >/dev/null
    docker cp "$nginx_conf" "$cid:/etc/nginx/conf.d/default.conf" >/dev/null
    docker exec "$cid" sh -lc 'nginx -t >/dev/null 2>&1 && nginx -s reload >/dev/null 2>&1 || true; find /var/cache/nginx -type f -delete 2>/dev/null || true' >/dev/null
  done
  log_ok "Running MFE containers are now synced from local v23.2 dist artifacts"
}

write_frontend_runtime_marker() {
  local cid marker
  cid="$(frontend_container_id)"
  [ -n "$cid" ] || return 1
  marker="nebulaops-v23.2-frontend-${cid}-$(date +%s)"
  printf '%s' "$marker" >"$ROOT_DIR/.nebulaops-runtime-owner.expected"
  docker exec "$cid" sh -lc "printf '%s' '$marker' >/usr/share/nginx/html/__nebulaops-runtime-owner.txt" >/dev/null
}

assert_public_url_targets_frontend_container() {
  local expected actual base
  expected="$(cat "$ROOT_DIR/.nebulaops-runtime-owner.expected" 2>/dev/null || true)"
  [ -n "$expected" ] || return 0
  while IFS= read -r base; do
    [ -z "$base" ] && continue
    actual="$(curl -fsS --max-time 5 -H 'Cache-Control: no-cache' -H 'Pragma: no-cache' "${base%/}/__nebulaops-runtime-owner.txt?v=$(date +%s)" 2>/dev/null || true)"
    [ "$actual" = "$expected" ] && { log_ok "${base%/} is served by the current frontend container"; return 0; }
  done < <(shell_probe_urls)
  log_err "${NEBULAOPS_PUBLIC_URL} is not serving the current frontend container content."
  docker ps --filter "publish=${NEBULAOPS_HTTP_PORT:-80}" --format '  {{.ID}}  {{.Names}}  {{.Ports}}' || true
  return 1
}

assert_container_remote_matches_local() {
  local slug="$1" cid local_file container_sha local_sha
  cid="$(frontend_container_id)"
  local_file="$ROOT_DIR/frontend/remotes/${slug}/dist/browser/remoteEntry.js"
  container_sha="$(docker exec "$cid" sh -lc "sha256sum /usr/share/nginx/html/remotes/${slug}/remoteEntry.js 2>/dev/null | awk '{print \$1}'" 2>/dev/null || true)"
  local_sha="$(sha256sum "$local_file" | awk '{print $1}')"
  if [ -z "$container_sha" ] || [ "$container_sha" != "$local_sha" ]; then
    log_err "Container/local SHA mismatch for ${slug}: local=${local_sha:-missing} container=${container_sha:-missing}"
    return 1
  fi
}

assert_served_remote_is_classic() {
  local slug="$1" body bridge_body
  body="$(fetch_remote_entry_body "$slug" || true)"
  [ -n "$body" ] || { log_err "MFE ${slug} did not serve remoteEntry.js"; return 1; }
  if printf '%s' "$body" | grep -Eq '\bexport[[:space:]]+(default|\{|class|function|const|let|var)'; then
    log_err "MFE ${slug} is still serving ESM syntax"
    return 1
  fi
  if ! printf '%s' "$body" | grep -Eq 'customElements\.define|classic standalone custom element'; then
    log_err "MFE ${slug} served remoteEntry.js is not shell-compatible"
    return 1
  fi
  if printf '%s' "$body" | grep -Eq 'NebulaOps v23.2 auth bridge|nebulaopsAuthBridge'; then
    log_ok "MFE ${slug} served remoteEntry.js is shell-compatible with inline auth bridge"
    return 0
  fi
  bridge_body="$(fetch_auth_bridge_body "$slug" || true)"
  if [ -n "$bridge_body" ]; then
    log_ok "MFE ${slug} served remoteEntry.js is shell-compatible; auth bridge is served as a separate asset"
    return 0
  fi
  log_err "MFE ${slug} is shell-compatible but no inline or separate auth bridge asset is reachable"
  return 1
}

log_step "Syncing v23.2 frontend runtime from local dist"
sync_frontend_runtime_container_from_local_dist
sync_mfe_runtime_containers_from_local_dist
write_frontend_runtime_marker

log_step "Verifying frontend container files and served same-origin endpoints"
assert_public_url_targets_frontend_container || exit 1
failed=0
for slug in "${remote_slugs[@]}"; do
  assert_container_remote_matches_local "$slug" || failed=1
  assert_served_remote_is_classic "$slug" || failed=1
done

if [ "$failed" -ne 0 ]; then
  log_err "Runtime sync finished, but same-origin verification still failed. Check port owners: docker ps --format '{{.Names}} {{.Ports}}' | grep ':80'"
  exit 1
fi

log_ok "NebulaOps v23.2 frontend runtime is fully synced and shell-compatible"
