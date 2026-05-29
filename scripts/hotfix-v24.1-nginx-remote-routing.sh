#!/usr/bin/env bash
# NebulaOps v24.1 hotfix/diagnostic for stale same-origin MFE remoteEntry.js.
# Run from the NebulaOps project root.
set -euo pipefail

ROOT_DIR="$(pwd)"
if [ ! -f "$ROOT_DIR/docker-compose.yml" ] || [ ! -f "$ROOT_DIR/frontend/nginx.conf" ]; then
  echo "[error] Run this script from the NebulaOps project root." >&2
  exit 1
fi

if [ -f "$ROOT_DIR/scripts/wsl/lib/common.sh" ]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/scripts/wsl/lib/common.sh"
else
  PROJECT_NAME="${COMPOSE_PROJECT_NAME:-nebulaops-v24-1}"
  COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
  NEBULAOPS_PUBLIC_URL="${NEBULAOPS_PUBLIC_URL:-http://nebulaops.localhost}"
  NEBULAOPS_HTTP_PORT="${NEBULAOPS_HTTP_PORT:-80}"
  dc(){ docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" "$@"; }
fi

remote_slugs=(
  platform-catalog incident-command-center runtime-readiness docker-storage-cleanup
  environment-configuration dependency-impact test-quality-dashboard docker-desktop
  openlens-kubernetes task-management observability cicd-gitops terraform-studio
  devsecops ai-ops finops-cost infra-hub release-center policy-center
  notification-center identity-admin progressive-delivery
)

sha_file(){ sha256sum "$1" | cut -d' ' -f1; }

frontend_cid="$(dc ps -q frontend 2>/dev/null | head -n 1)"
if [ -z "$frontend_cid" ]; then
  echo "[error] frontend container is not running." >&2
  exit 1
fi

echo "[info] frontend container: $frontend_cid"
echo "[info] rewriting frontend Nginx conf.d to remove any old /remotes proxy config"
docker exec "$frontend_cid" sh -lc 'mkdir -p /tmp/nebulaops-nginx-backup && cp -a /etc/nginx/conf.d/. /tmp/nebulaops-nginx-backup/ 2>/dev/null || true && rm -f /etc/nginx/conf.d/*.conf'
docker cp "$ROOT_DIR/frontend/nginx.conf" "$frontend_cid:/etc/nginx/conf.d/default.conf"

echo "[info] syncing shell dist and MFE dist into frontend container"
docker exec "$frontend_cid" sh -lc 'rm -rf /usr/share/nginx/html/* && mkdir -p /usr/share/nginx/html/remotes'
docker cp "$ROOT_DIR/frontend/dist/nebulaops/browser/." "$frontend_cid:/usr/share/nginx/html/"
docker exec "$frontend_cid" sh -lc 'mkdir -p /usr/share/nginx/html/remotes'
for slug in "${remote_slugs[@]}"; do
  src="$ROOT_DIR/frontend/remotes/$slug/dist/browser"
  if [ ! -f "$src/remoteEntry.js" ]; then
    echo "[error] missing local dist for $slug: $src/remoteEntry.js" >&2
    exit 1
  fi
  docker exec "$frontend_cid" sh -lc "rm -rf '/usr/share/nginx/html/remotes/$slug' && mkdir -p '/usr/share/nginx/html/remotes/$slug'"
  docker cp "$src/." "$frontend_cid:/usr/share/nginx/html/remotes/$slug/"
done

echo "[info] validating and reloading Nginx"
docker exec "$frontend_cid" nginx -t
docker exec "$frontend_cid" nginx -s reload
sleep 2

echo "[info] active /remotes location from nginx -T:"
docker exec "$frontend_cid" sh -lc "nginx -T 2>/dev/null | sed -n '/location \\^~ \\/remotes\\//,/}/p' | head -40"

echo "[info] checking public owner marker"
marker="nebulaops-v24.1-hotfix-${frontend_cid}-$(date +%s)"
printf '%s' "$marker" > "$ROOT_DIR/.nebulaops-runtime-owner.expected"
docker exec "$frontend_cid" sh -lc "printf '%s' '$marker' >/usr/share/nginx/html/__nebulaops-runtime-owner.txt"
public_marker="$(curl -fsS --max-time 8 -H 'Cache-Control: no-cache' -H 'Pragma: no-cache' "${NEBULAOPS_PUBLIC_URL%/}/__nebulaops-runtime-owner.txt?hotfix=$(date +%s)" 2>/dev/null || true)"
echo "[info] marker expected=$marker actual=${public_marker:-missing}"
if [ "$public_marker" != "$marker" ]; then
  echo "[error] ${NEBULAOPS_PUBLIC_URL%/} is not served by the current frontend container." >&2
  docker ps --filter "publish=${NEBULAOPS_HTTP_PORT:-80}" --format '  {{.ID}}  {{.Names}}  {{.Ports}}' || true
  exit 1
fi

echo "[info] SHA comparison: local vs frontend-container vs internal-nginx vs public"
failed=0
tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

for slug in "${remote_slugs[@]}"; do
  local_file="$ROOT_DIR/frontend/remotes/$slug/dist/browser/remoteEntry.js"
  public_file="$tmpdir/${slug}.public.remoteEntry.js"

  local_sha="$(sha_file "$local_file")"
  container_sha="$(docker exec "$frontend_cid" sh -lc "sha256sum '/usr/share/nginx/html/remotes/$slug/remoteEntry.js' 2>/dev/null | cut -d' ' -f1" || true)"
  internal_sha="$(docker exec "$frontend_cid" sh -lc "wget -q -O - 'http://127.0.0.1/remotes/$slug/remoteEntry.js?hotfix=$(date +%s)' | sha256sum | cut -d' ' -f1" || true)"

  if curl -fsS --max-time 8 -H 'Cache-Control: no-cache' -H 'Pragma: no-cache' "${NEBULAOPS_PUBLIC_URL%/}/remotes/$slug/remoteEntry.js?hotfix=$(date +%s)" -o "$public_file" 2>/dev/null; then
    public_sha="$(sha_file "$public_file")"
  else
    public_sha="missing"
    : > "$public_file"
  fi

  printf '%-32s local=%s container=%s internal=%s public=%s\n' "$slug" "$local_sha" "${container_sha:-missing}" "${internal_sha:-missing}" "${public_sha:-missing}"

  if [ "$local_sha" != "${container_sha:-}" ] || [ "$local_sha" != "${internal_sha:-}" ] || [ "$local_sha" != "${public_sha:-}" ]; then
    failed=1
    echo "[debug] first public bytes for $slug:" >&2
    head -c 220 "$public_file" >&2 || true
    printf '\n' >&2
  fi

  if ! grep -Eq 'NebulaOps v24.1 auth bridge|nebulaopsAuthBridge' "$public_file"; then
    echo "[error] public $slug is still missing the auth bridge" >&2
    failed=1
  fi
done

if [ "$failed" -ne 0 ]; then
  echo "[error] Remote routing is still inconsistent." >&2
  echo "[hint] If local=container=internal but public differs, another process/proxy is serving port ${NEBULAOPS_HTTP_PORT:-80}." >&2
  echo "[hint] Run: docker ps --format '{{.Names}} {{.Ports}}' | grep ':${NEBULAOPS_HTTP_PORT:-80}->'" >&2
  exit 1
fi

echo "[ok] v24.1 frontend remote routing is consistent."
