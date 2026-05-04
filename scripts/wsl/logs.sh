#!/usr/bin/env bash
# v23.3 — Tail service logs. Usage: ./logs.sh [service-name] [--lines N]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

if [ $# -eq 0 ]; then
  log_info "Available services:"
  dc config --services | sort | sed 's/^/  /'
  echo
  log_info "Usage: $0 <service-name> [--lines N]"
  exit 0
fi

SVC="$1"; shift || true
LINES=200
while [ $# -gt 0 ]; do
  case "$1" in
    --lines|-n) LINES="$2"; shift 2 ;;
    *) shift ;;
  esac
done

dc logs --tail="$LINES" -f "$SVC"
