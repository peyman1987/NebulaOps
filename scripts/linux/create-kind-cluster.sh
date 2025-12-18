#!/usr/bin/env bash
set -euo pipefail
CLUSTER=${1:-nebulaops-v22-3}
kind get clusters | grep -qx "$CLUSTER" || kind create cluster --name "$CLUSTER" --config infrastructure/kind/cluster.yaml
kubectl config use-context "kind-${CLUSTER}"
kubectl create namespace nebulaops --dry-run=client -o yaml | kubectl apply -f -
kubectl cluster-info
