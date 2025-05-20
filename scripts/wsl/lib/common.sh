#!/usr/bin/env bash
# v21.2 — Shared helpers sourced by all WSL scripts.
# Provides logging, status indicators, project naming, compose helpers.

# Project naming — derived once, consumed by all scripts
export PROJECT_NAME="${COMPOSE_PROJECT_NAME:-nebulaops-v21-2-1}"
export ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
export COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
export CONFIG_FILE="$ROOT_DIR/config/platform.yml"

# ANSI colors (no-op when not a TTY)
if [ -t 1 ]; then
  C_RESET='\033[0m'; C_BOLD='\033[1m'
  C_GREEN='\033[32m'; C_YELLOW='\033[33m'; C_RED='\033[31m'
  C_CYAN='\033[36m'; C_DIM='\033[2m'
else
  C_RESET=''; C_BOLD=''; C_GREEN=''; C_YELLOW=''; C_RED=''; C_CYAN=''; C_DIM=''
fi

log_info()  { printf "${C_CYAN}ℹ${C_RESET}  %s\n" "$*"; }
log_ok()    { printf "${C_GREEN}✓${C_RESET}  %s\n" "$*"; }
log_warn()  { printf "${C_YELLOW}⚠${C_RESET}  %s\n" "$*"; }
log_err()   { printf "${C_RED}✗${C_RESET}  %s\n" "$*" >&2; }
log_step()  { printf "\n${C_BOLD}${C_CYAN}▶ %s${C_RESET}\n" "$*"; }

dc()  { docker compose -p "$PROJECT_NAME" -f "$COMPOSE_FILE" "$@"; }

check_command() {
  command -v "$1" >/dev/null 2>&1 \
    && log_ok "$1 found" \
    || { log_err "$1 missing"; return 1; }
}

# Wait for a HTTP endpoint with timeout
wait_http() {
  local url="$1" timeout="${2:-60}" label="${3:-endpoint}"
  for ((i=1; i<=timeout; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      log_ok "$label is reachable ($url)"
      return 0
    fi
    sleep 1
  done
  log_warn "$label not ready after ${timeout}s ($url)"
  return 1
}
