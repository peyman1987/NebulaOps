#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
helm upgrade --install nebulaops "$ROOT_DIR/infrastructure/helm/nebulaops" \
  --namespace nebulaops \
  --create-namespace \
  --set global.imagePullPolicy=IfNotPresent
kubectl -n nebulaops get pods
cat <<'INFO'

Useful port-forwards:
  kubectl -n nebulaops port-forward svc/nebulaops-frontend 4200:4200
  kubectl -n nebulaops port-forward svc/nebulaops-gateway 8080:8080
  kubectl -n nebulaops port-forward svc/nebulaops-grafana 3000:3000
  kubectl -n nebulaops port-forward svc/nebulaops-prometheus 9090:9090

Grafana login: admin / admin
INFO
