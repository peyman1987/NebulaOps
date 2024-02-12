#!/usr/bin/env bash

set -euo pipefail

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() {
  echo -e "${GREEN}[INFO]${NC} $1"
}

warn() {
  echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
  echo -e "${RED}[ERROR]${NC} $1"
}

echo
log "NebulaOps Kubernetes Bootstrap (k3s)"
echo

# -----------------------------------------------------------------------------
# Dependencies
# -----------------------------------------------------------------------------

sudo apt-get update

sudo apt-get install -y \
  ca-certificates \
  curl \
  apt-transport-https \
  gnupg

# -----------------------------------------------------------------------------
# kubectl install
# -----------------------------------------------------------------------------

if ! command -v kubectl >/dev/null 2>&1; then

  log "Installing kubectl..."

  sudo install -m 0755 -d /etc/apt/keyrings

  curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.30/deb/Release.key \
    | sudo gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg

  echo \
  'deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.30/deb/ /' \
  | sudo tee /etc/apt/sources.list.d/kubernetes.list >/dev/null

  sudo apt-get update
  sudo apt-get install -y kubectl
fi

echo
kubectl version --client
echo

# -----------------------------------------------------------------------------
# k3s install
# -----------------------------------------------------------------------------

if ! systemctl is-active --quiet k3s; then

  log "Installing k3s cluster..."

  curl -sfL https://get.k3s.io | sh -
fi

# -----------------------------------------------------------------------------
# kubeconfig
# -----------------------------------------------------------------------------

mkdir -p ~/.kube

sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config

sudo chown "$USER:$USER" ~/.kube/config

chmod 600 ~/.kube/config

export KUBECONFIG=$HOME/.kube/config

# -----------------------------------------------------------------------------
# Cluster validation
# -----------------------------------------------------------------------------

echo
log "Checking cluster connectivity..."
echo

kubectl get nodes

# -----------------------------------------------------------------------------
# Namespace
# -----------------------------------------------------------------------------

if kubectl get namespace nebulaops >/dev/null 2>&1; then
  log "Namespace nebulaops already exists."
else
  log "Creating namespace nebulaops..."
  kubectl create namespace nebulaops
fi

# -----------------------------------------------------------------------------
# Final diagnostics
# -----------------------------------------------------------------------------

echo
log "Cluster namespaces:"
kubectl get ns

echo
log "Cluster nodes:"
kubectl get nodes -o wide

echo
log "NebulaOps Kubernetes environment ready."
echo