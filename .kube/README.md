# Runtime kubeconfig

This directory is intentionally shipped without a kubeconfig.

During startup `scripts/wsl/prepare-kubeconfig-for-docker.sh` copies the operator's real kubeconfig from `$KUBECONFIG`, `/etc/rancher/k3s/k3s.yaml`, or `$HOME/.kube/config` into `.kube/config` so Docker containers can access the selected Kubernetes context.
