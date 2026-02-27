#!/usr/bin/env bash
set -euo pipefail
NAMESPACE=${1:-nebulaops}
helm dependency update infrastructure/helm/nebulaops || true
helm upgrade --install nebulaops-v22-5 infrastructure/helm/nebulaops -n "$NAMESPACE" --create-namespace --values infrastructure/helm/nebulaops/values.yaml
kubectl -n "$NAMESPACE" get pods,svc,ingress
