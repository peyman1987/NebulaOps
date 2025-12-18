#!/usr/bin/env bash
# NebulaOps v22.3 local frontend builder.
# Builds Angular shell and all MFE dist folders on the host/WSL, then Dockerfiles only copy dist into nginx.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if ! command -v npm >/dev/null 2>&1; then
  echo "[ERROR] npm is required on the host/WSL to build frontend dist folders."
  exit 1
fi

build_project() {
  local dir="$1"
  local name="$2"
  echo
  echo "==> Building ${name}"
  cd "$ROOT_DIR/$dir"

  if [ -f package-lock.json ]; then
    npm ci --legacy-peer-deps --no-audit --no-fund
  else
    npm install --legacy-peer-deps --no-audit --no-fund
  fi

  npm run build
}

build_project "frontend" "frontend shell"

for remote_dir in "$ROOT_DIR"/frontend/remotes/*; do
  [ -d "$remote_dir" ] || continue
  [ -f "$remote_dir/package.json" ] || continue
  rel="${remote_dir#$ROOT_DIR/}"
  name="$(basename "$remote_dir")"
  build_project "$rel" "MFE ${name}"
done

echo
echo "[NebulaOps] Local frontend dist build completed."
echo "You can now run:"
echo "  docker compose build frontend"
echo "  ./scripts/wsl/build-frontend-images.sh"
