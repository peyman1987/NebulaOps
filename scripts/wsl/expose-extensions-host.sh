#!/usr/bin/env bash
# v23.1 — Expose Kubernetes extensions to the Windows/WSL host loopback.
#
# Why this exists:
#   k3s NodePort works from inside WSL, but Windows Chrome can still see
#   ERR_CONNECTION_REFUSED on localhost:31110..31120 because WSL localhost
#   forwarding does not always proxy kube-proxy/iptables NodePorts.
#   A real kubectl port-forward process is visible to Windows localhost.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

K8S_NAMESPACE="${NEBULAOPS_K8S_NAMESPACE:-nebulaops}"
BIND_ADDRESS="${NEBULAOPS_EXTENSIONS_BIND_ADDRESS:-0.0.0.0}"
PF_DIR="${NEBULAOPS_EXTENSIONS_PF_DIR:-$ROOT_DIR/.runtime/extensions-port-forward}"
WAIT_SECONDS="${NEBULAOPS_EXTENSIONS_HOST_WAIT_SECONDS:-45}"
STOP_ONLY=false
WAIT_ONLY=false

EXTENSIONS=(
  apiforge
  kubebridge
  contract-hub
)

usage() {
  cat <<USAGE
Usage: $0 [--stop] [--wait-only]

Starts kubectl port-forward bridges for all NebulaOps Kubernetes extensions so
Windows/WSL browsers can open the installed extension URLs reliably.

Environment:
  NEBULAOPS_K8S_NAMESPACE=nebulaops
  NEBULAOPS_EXTENSIONS_BIND_ADDRESS=0.0.0.0
  NEBULAOPS_EXTENSIONS_HOST_WAIT_SECONDS=45
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --stop) STOP_ONLY=true; shift ;;
    --wait-only) WAIT_ONLY=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) log_err "Unknown argument: $1"; usage; exit 1 ;;
  esac
done

port_for() {
  case "$1" in
    apiforge) echo 31110 ;;
    kubebridge) echo 31111 ;;
    runbook-center) echo 31112 ;;
    extension-registry) echo 31113 ;;
    contract-hub) echo 31114 ;;
    eventops-center) echo 31115 ;;
    observability-lens) echo 31116 ;;
    gitops-center) echo 31117 ;;
    secrets-config-center) echo 31118 ;;
    slo-center) echo 31119 ;;
    backup-recovery-center) echo 31120 ;;
    *) return 1 ;;
  esac
}

path_for() {
  case "$1" in
    apiforge) echo /apiforge/ ;;
    kubebridge) echo /kubebridge/ ;;
    runbook-center) echo /runbook-center/ ;;
    extension-registry) echo /extension-registry/ ;;
    contract-hub) echo /contract-hub/ ;;
    eventops-center) echo /eventops-center/ ;;
    observability-lens) echo /observability-lens/ ;;
    gitops-center) echo /gitops-center/ ;;
    secrets-config-center) echo /secrets-config-center/ ;;
    slo-center) echo /slo-center/ ;;
    backup-recovery-center) echo /backup-recovery-center/ ;;
    *) return 1 ;;
  esac
}

health_path_for() {
  case "$1" in
    apiforge) echo /apiforge/actuator/health ;;
    *) echo /healthz ;;
  esac
}

pid_file_for() { echo "$PF_DIR/$1.pid"; }
log_file_for() { echo "$PF_DIR/$1.log"; }

is_pid_alive() {
  local pid="$1"
  [ -n "$pid" ] && kill -0 "$pid" >/dev/null 2>&1
}

stop_one() {
  local ext="$1" pf pid
  pf="$(pid_file_for "$ext")"
  if [ -f "$pf" ]; then
    pid="$(cat "$pf" 2>/dev/null || true)"
    if is_pid_alive "$pid"; then
      log_info "Stopping host bridge for $ext (pid $pid)"
      kill "$pid" >/dev/null 2>&1 || true
      sleep 0.2
      is_pid_alive "$pid" && kill -9 "$pid" >/dev/null 2>&1 || true
    fi
    rm -f "$pf"
  fi

  # Clean up stale kubectl port-forward processes from older runs even when pid files were lost.
  pkill -f "kubectl .*port-forward .*svc/${ext} " >/dev/null 2>&1 || true
}

health_ok() {
  local ext="$1" port path
  port="$(port_for "$ext")"
  path="$(health_path_for "$ext")"
  curl -fsS "http://127.0.0.1:${port}${path}" >/dev/null 2>&1
}

wait_one() {
  local ext="$1" port public_path deadline
  port="$(port_for "$ext")"
  public_path="$(path_for "$ext")"
  deadline=$((SECONDS + WAIT_SECONDS))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if health_ok "$ext"; then
      log_ok "$ext host URL ready: http://localhost:${port}${public_path}"
      return 0
    fi
    sleep 1
  done
  log_err "$ext did not become reachable on http://localhost:${port}${public_path}"
  log_err "Inspect: $(log_file_for "$ext")"
  [ -f "$(log_file_for "$ext")" ] && tail -20 "$(log_file_for "$ext")" || true
  return 1
}

start_one() {
  local ext="$1" port log_file pid_file pid
  port="$(port_for "$ext")"
  log_file="$(log_file_for "$ext")"
  pid_file="$(pid_file_for "$ext")"

  kubectl -n "$K8S_NAMESPACE" get svc "$ext" >/dev/null 2>&1 || {
    log_err "Service not found for extension '$ext' in namespace '$K8S_NAMESPACE'"
    return 1
  }

  stop_one "$ext"
  : > "$log_file"
  log_step "Starting host bridge for $ext on localhost:${port}"
  nohup kubectl -n "$K8S_NAMESPACE" port-forward --address "$BIND_ADDRESS" "svc/${ext}" "${port}:8080" >"$log_file" 2>&1 &
  pid=$!
  echo "$pid" > "$pid_file"
  sleep 0.5

  if ! is_pid_alive "$pid"; then
    # If something else already serves a healthy response on this port, keep startup green.
    if health_ok "$ext"; then
      log_warn "$ext port-forward process exited, but localhost:${port} is already healthy"
      return 0
    fi
    log_err "Host bridge for $ext exited immediately. Log: $log_file"
    cat "$log_file" || true
    return 1
  fi

  wait_one "$ext"
}

mkdir -p "$PF_DIR"

if [ "$STOP_ONLY" = "true" ]; then
  for ext in "${EXTENSIONS[@]}"; do stop_one "$ext"; done
  log_ok "NebulaOps extension host bridges stopped"
  exit 0
fi

if ! command -v kubectl >/dev/null 2>&1; then
  log_err "kubectl is required to expose extensions to the host"
  exit 1
fi

if ! kubectl cluster-info >/dev/null 2>&1; then
  log_err "No Kubernetes cluster is reachable from kubectl"
  exit 1
fi

failed=0
if [ "$WAIT_ONLY" = "true" ]; then
  for ext in "${EXTENSIONS[@]}"; do wait_one "$ext" || failed=1; done
else
  for ext in "${EXTENSIONS[@]}"; do start_one "$ext" || failed=1; done
fi

if [ "$failed" -ne 0 ]; then
  log_err "One or more extension host bridges failed"
  exit 1
fi

log_ok "All NebulaOps extension host URLs are ready for the browser"
