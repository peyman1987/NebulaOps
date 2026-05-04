#!/usr/bin/env bash
set -Eeuo pipefail

SERVICE_NAME="${SERVICE_NAME:-spring-mvc-service}"
SERVICE_PORT="${SERVICE_PORT:-8099}"
COMPOSE_BASE_FILE="${COMPOSE_BASE_FILE:-docker-compose.yml}"
COMPOSE_ADDON_FILE="${COMPOSE_ADDON_FILE:-docker-compose.spring-mvc-service.yml}"

SHOW_LOGS=false
BUILD_ONLY=false
DOWN_ONLY=false
INSTALL_ONLY=false
NO_INSTALL=false
NEBULA_ROOT_ARG=""

log() { printf '\033[1;34m[deploy]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warning]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<EOF
Usage:
  ./scripts/deploy.sh [path-nebulaops-v23.3] [options]
  ./deploy.sh [options]

From the add-on directory:
  ./scripts/deploy.sh
  ./scripts/deploy.sh ../nebulaops-v23.3
  ./scripts/deploy.sh --logs
  ./scripts/deploy.sh --build-only
  ./scripts/deploy.sh --install-only

From the installed demo app directory:
  ./deploy.sh
  ./deploy.sh --logs
  ./deploy.sh --down

Options:
  --logs          show logs after startup
  --build-only    run docker compose build only
  --down, --stop  stop the service
  --install-only  install/copy the add-on only, without starting Docker
  --no-install    skip install-into-nebulaops.sh
  -h, --help      show this help

Variables:
  NEBULA_ROOT=/path/to/nebulaops-v23.3
  SERVICE_PORT=8099
  SERVICE_NAME=spring-mvc-service
  SPRING_MVC_IMAGE=registry.example.com/spring-mvc-service:tag
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --logs) SHOW_LOGS=true; shift ;;
    --build-only) BUILD_ONLY=true; shift ;;
    --down|--stop) DOWN_ONLY=true; shift ;;
    --install-only) INSTALL_ONLY=true; shift ;;
    --no-install) NO_INSTALL=true; shift ;;
    -h|--help) usage; exit 0 ;;
    -*) fail "Unknown option: $1" ;;
    *)
      if [[ -z "$NEBULA_ROOT_ARG" ]]; then
        NEBULA_ROOT_ARG="$1"
        shift
      else
        fail "Unrecognized extra argument: $1"
      fi
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$(pwd)"

is_nebula_root() {
  local d="$1"
  [[ -d "$d/backend" && -f "$d/$COMPOSE_BASE_FILE" ]]
}

is_addon_root() {
  local d="$1"
  [[ -f "$d/scripts/install-into-nebulaops.sh" && -d "$d/backend/$SERVICE_NAME" ]]
}

is_app_demo_root() {
  local d="$1"
  [[ -f "$d/pom.xml" && -d "$d/src/main" ]]
}

detect_addon_root() {
  local c

  c="$(cd "$SCRIPT_DIR/.." 2>/dev/null && pwd || true)"
  if [[ -n "$c" ]] && is_addon_root "$c"; then
    echo "$c"
    return 0
  fi

  c="$SCRIPT_DIR"
  if [[ -n "$c" ]] && is_addon_root "$c"; then
    echo "$c"
    return 0
  fi

  c="$RUN_DIR"
  if [[ -n "$c" ]] && is_addon_root "$c"; then
    echo "$c"
    return 0
  fi

  return 1
}

detect_app_root() {
  if is_app_demo_root "$SCRIPT_DIR"; then
    echo "$SCRIPT_DIR"
    return 0
  fi

  if is_app_demo_root "$RUN_DIR"; then
    echo "$RUN_DIR"
    return 0
  fi

  return 1
}

resolve_nebula_root() {
  local addon_root="${1:-}"
  local app_root="${2:-}"
  local c
  local parent

  if [[ -n "$NEBULA_ROOT_ARG" ]]; then
    c="$(cd "$NEBULA_ROOT_ARG" 2>/dev/null && pwd)" || fail "NebulaOps path not found: $NEBULA_ROOT_ARG"
    is_nebula_root "$c" || fail "Invalid NebulaOps path: $c"
    echo "$c"
    return 0
  fi

  if [[ -n "${NEBULA_ROOT:-}" ]]; then
    c="$(cd "$NEBULA_ROOT" 2>/dev/null && pwd)" || fail "NEBULA_ROOT not found: $NEBULA_ROOT"
    is_nebula_root "$c" || fail "Invalid NEBULA_ROOT: $c"
    echo "$c"
    return 0
  fi

  if [[ -n "$app_root" ]]; then
    c="$(cd "$app_root/../.." 2>/dev/null && pwd || true)"
    if [[ -n "$c" ]] && is_nebula_root "$c"; then
      echo "$c"
      return 0
    fi
  fi

  if [[ -n "$addon_root" ]]; then
    c="$(cd "$addon_root/../nebulaops-v23.3" 2>/dev/null && pwd || true)"
    if [[ -n "$c" ]] && is_nebula_root "$c"; then
      echo "$c"
      return 0
    fi

    parent="$(cd "$addon_root/.." 2>/dev/null && pwd || true)"
    if [[ -n "$parent" ]]; then
      while IFS= read -r c; do
        if [[ -n "$c" ]] && is_nebula_root "$c"; then
          echo "$c"
          return 0
        fi
      done < <(find "$parent" -maxdepth 1 -type d \( -iname 'nebulaops-v23.3' -o -iname 'nebulaops-v*' \) 2>/dev/null | sort)
    fi
  fi

  fail "Cannot find nebulaops-v23.3. Use: ./scripts/deploy.sh ../nebulaops-v23.3"
}

docker_compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
  else
    fail "Docker Compose was not found. Install Docker Compose v2 or docker-compose."
  fi
}

run_docker_deploy() {
  local nebula_root="$1"

  command -v docker >/dev/null 2>&1 || fail "Docker was not found in PATH."
  [[ -f "$nebula_root/$COMPOSE_BASE_FILE" ]] || fail "Missing $nebula_root/$COMPOSE_BASE_FILE"
  [[ -f "$nebula_root/$COMPOSE_ADDON_FILE" ]] || fail "Missing $nebula_root/$COMPOSE_ADDON_FILE"

  cd "$nebula_root"

  log "NebulaOps root: $nebula_root"
  log "Service: $SERVICE_NAME"
  [[ -n "${SPRING_MVC_IMAGE:-}" ]] && log "Image override: $SPRING_MVC_IMAGE"

  log "Creating Docker network nebulaops-network if it does not exist."
  docker network create nebulaops-network >/dev/null 2>&1 || true

  log "Validating Docker Compose."
  docker_compose -f "$COMPOSE_BASE_FILE" -f "$COMPOSE_ADDON_FILE" config >/dev/null

  if [[ "$DOWN_ONLY" == true ]]; then
    log "Stopping $SERVICE_NAME."
    docker_compose -f "$COMPOSE_BASE_FILE" -f "$COMPOSE_ADDON_FILE" stop "$SERVICE_NAME"
    exit 0
  fi

  if [[ "$BUILD_ONLY" == true ]]; then
    log "Docker build for $SERVICE_NAME."
    docker_compose -f "$COMPOSE_BASE_FILE" -f "$COMPOSE_ADDON_FILE" build "$SERVICE_NAME"
    exit 0
  fi

  log "Building and starting $SERVICE_NAME."
  docker_compose -f "$COMPOSE_BASE_FILE" -f "$COMPOSE_ADDON_FILE" up -d --build "$SERVICE_NAME"

  log "Container status."
  docker_compose -f "$COMPOSE_BASE_FILE" -f "$COMPOSE_ADDON_FILE" ps "$SERVICE_NAME"

  cat <<EOF

Useful endpoints:
  Home MVC:        http://localhost:${SERVICE_PORT}
  API info:        http://localhost:${SERVICE_PORT}/api/spring-mvc/info
  Health:          http://localhost:${SERVICE_PORT}/actuator/health
  Prometheus:      http://localhost:${SERVICE_PORT}/actuator/prometheus
  Swagger UI:      http://localhost:${SERVICE_PORT}/swagger-ui.html

Log:
  cd $nebula_root
  docker compose -f $COMPOSE_BASE_FILE -f $COMPOSE_ADDON_FILE logs -f $SERVICE_NAME

EOF

  if [[ "$SHOW_LOGS" == true ]]; then
    log "Logs for $SERVICE_NAME. Press CTRL+C to exit."
    docker_compose -f "$COMPOSE_BASE_FILE" -f "$COMPOSE_ADDON_FILE" logs -f "$SERVICE_NAME"
  fi
}

ADDON_ROOT="$(detect_addon_root || true)"
APP_ROOT="$(detect_app_root || true)"

if [[ -n "$ADDON_ROOT" ]]; then
  NEBULA_ROOT_RESOLVED="$(resolve_nebula_root "$ADDON_ROOT" "")"

  log "Mode: add-on"
  log "Add-on root: $ADDON_ROOT"
  log "NebulaOps root: $NEBULA_ROOT_RESOLVED"

  if [[ "$NO_INSTALL" == false ]]; then
    INSTALL_SCRIPT="$ADDON_ROOT/scripts/install-into-nebulaops.sh"
    [[ -f "$INSTALL_SCRIPT" ]] || fail "Missing $INSTALL_SCRIPT"
    chmod +x "$INSTALL_SCRIPT"
    log "Installing/updating add-on inside NebulaOps."
    "$INSTALL_SCRIPT" "$NEBULA_ROOT_RESOLVED"
  else
    log "Installation skipped (--no-install)."
  fi

  TARGET_APP_DIR="$NEBULA_ROOT_RESOLVED/backend/$SERVICE_NAME"
  [[ -d "$TARGET_APP_DIR" ]] || fail "Demo app not found: $TARGET_APP_DIR"

  log "Copying deploy.sh into the demo app."
  cp "${BASH_SOURCE[0]}" "$TARGET_APP_DIR/deploy.sh"
  chmod +x "$TARGET_APP_DIR/deploy.sh"

  if [[ "$INSTALL_ONLY" == true ]]; then
    log "Installation completed."
    exit 0
  fi

  run_docker_deploy "$NEBULA_ROOT_RESOLVED"
  exit 0
fi

if [[ -n "$APP_ROOT" ]]; then
  NEBULA_ROOT_RESOLVED="$(resolve_nebula_root "" "$APP_ROOT")"

  log "Mode: demo app"
  log "App root: $APP_ROOT"

  run_docker_deploy "$NEBULA_ROOT_RESOLVED"
  exit 0
fi

fail "Unrecognized path. Run from nebulaops-spring-mvc-addon with ./scripts/deploy.sh or from backend/spring-mvc-service with ./deploy.sh"
