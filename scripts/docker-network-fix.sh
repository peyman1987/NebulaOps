#!/usr/bin/env bash
set -euo pipefail
NETWORK_NAME="${NEBULAOPS_NETWORK_NAME:-nebulaops-network}"
if docker network inspect "$NETWORK_NAME" >/dev/null 2>&1; then
  echo "[ok] Docker network already exists: $NETWORK_NAME"
  docker network inspect "$NETWORK_NAME" --format 'driver={{.Driver}} scope={{.Scope}} labels={{json .Labels}}' || true
else
  echo "[info] Creating Docker network: $NETWORK_NAME"
  docker network create "$NETWORK_NAME" >/dev/null
  echo "[ok] Docker network created: $NETWORK_NAME"
fi
