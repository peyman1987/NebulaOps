#!/usr/bin/env bash
set -euo pipefail
if [[ $EUID -eq 0 ]]; then SUDO=""; else SUDO="sudo"; fi
. /etc/os-release
if [[ "${ID}" != "ubuntu" && "${ID_LIKE:-}" != *debian* ]]; then echo "This installer targets Ubuntu/Debian based WSL/Linux."; exit 1; fi
$SUDO apt-get update
$SUDO apt-get install -y ca-certificates curl gnupg lsb-release apt-transport-https software-properties-common jq yq make git openjdk-21-jdk maven nodejs npm
$SUDO install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | $SUDO gpg --dearmor -o /etc/apt/keyrings/docker.gpg
$SUDO chmod a+r /etc/apt/keyrings/docker.gpg
ARCH=$(dpkg --print-architecture)
CODENAME=$(. /etc/os-release && echo "${VERSION_CODENAME:-noble}")
echo "deb [arch=${ARCH} signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${CODENAME} stable" | $SUDO tee /etc/apt/sources.list.d/docker.list >/dev/null
$SUDO apt-get update
$SUDO apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
$SUDO usermod -aG docker "$USER" || true
curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.31/deb/Release.key | $SUDO gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
$SUDO chmod 644 /etc/apt/keyrings/kubernetes-apt-keyring.gpg
echo 'deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.31/deb/ /' | $SUDO tee /etc/apt/sources.list.d/kubernetes.list >/dev/null
$SUDO apt-get update && $SUDO apt-get install -y kubectl
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.24.0/kind-linux-amd64 && chmod +x ./kind && $SUDO mv ./kind /usr/local/bin/kind
$SUDO systemctl enable --now docker 2>/dev/null || $SUDO service docker start
newgrp docker <<'EONG' || true
docker version
docker compose version
kubectl version --client=true
helm version
kind version
EONG
echo "Native Docker Engine, Docker Compose plugin, kubectl, Helm and kind installed. Reopen the shell if docker permission is not active yet."
