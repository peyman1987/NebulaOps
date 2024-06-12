#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$ROOT/backups/$STAMP"
mkdir -p "$OUT"
cp -a "$ROOT/infrastructure/terraform" "$OUT/terraform" 2>/dev/null || true
cp -a "$ROOT/infrastructure/observability/grafana" "$OUT/grafana" 2>/dev/null || true
if command -v docker >/dev/null 2>&1; then
  docker compose -f "$ROOT/docker-compose.yml" ps > "$OUT/docker-compose-ps.txt" 2>&1 || true
fi
ln -sfn "$OUT" "$ROOT/backups/latest"
echo "Backup created: $OUT"
