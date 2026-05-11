#!/usr/bin/env bash
# NebulaOps v23.4 — Remove platform extension runtime resources from Kubernetes.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"
K8S_NAMESPACE="${NEBULAOPS_EXTENSIONS_NAMESPACE:-nebulaops}"
DELETE_DATA=false
SELECTED=(apiforge kubebridge contract-hub)
while [ "$#" -gt 0 ]; do
  case "$1" in
    --delete-data) DELETE_DATA=true; shift ;;
    --only) [ "$#" -ge 2 ] || { log_err "--only requires an extension slug"; exit 1; }; SELECTED=("$2"); shift 2 ;;
    -h|--help)
      cat <<USAGE
Usage: $0 [--only <extension>] [--delete-data]
  --delete-data   Also delete APIForge PVC when APIForge is selected.
USAGE
      exit 0 ;;
    *) log_err "Unknown argument: $1"; exit 1 ;;
  esac
done
for ext in "${SELECTED[@]}"; do
  log_step "Removing extension: $ext"
  kubectl -n "$K8S_NAMESPACE" delete ingress "$ext" --ignore-not-found=true
  kubectl -n "$K8S_NAMESPACE" delete service "$ext" --ignore-not-found=true
  kubectl -n "$K8S_NAMESPACE" delete deployment "$ext" --ignore-not-found=true
  if [ "$ext" = "apiforge" ] && [ "$DELETE_DATA" = "true" ]; then
    kubectl -n "$K8S_NAMESPACE" delete pvc apiforge-data --ignore-not-found=true
    log_warn "APIForge PVC deleted"
  fi
done
log_ok "Selected NebulaOps extension resources removed"
