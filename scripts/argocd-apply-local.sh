#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

kubectl get namespace argocd >/dev/null 2>&1 || kubectl create namespace argocd
kubectl apply -f infrastructure/argocd/project.yaml
kubectl apply -f infrastructure/argocd/application.yaml

echo "Argo CD Application applied. Check status with: kubectl -n argocd get applications"
