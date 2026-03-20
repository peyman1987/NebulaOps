#!/usr/bin/env bash
# NebulaOps v23.1 — Diagnose APIForge Kubernetes deployment.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"
K8S_NAMESPACE="${NEBULAOPS_EXTENSIONS_NAMESPACE:-nebulaops}"
log_step "Kubernetes context"
kubectl config current-context 2>/dev/null || true
kubectl get nodes -o wide || true
log_step "APIForge resources"
kubectl -n "$K8S_NAMESPACE" get deploy,pods,svc,ingress -l app=apiforge -o wide || true
log_step "APIForge deployment"
kubectl -n "$K8S_NAMESPACE" describe deployment apiforge || true
log_step "APIForge pods"
kubectl -n "$K8S_NAMESPACE" describe pod -l app=apiforge || true
log_step "Recent events"
kubectl -n "$K8S_NAMESPACE" get events --sort-by=.lastTimestamp | tail -80 || true
log_step "Local images"
docker image ls | grep -E 'nebulaops-v23-1-apiforge|localhost:5001/nebulaops-v23-1-apiforge' || true
log_step "Suggested redeploy"
echo "./scripts/wsl/deploy-extensions-k8s.sh"
