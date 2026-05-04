#!/usr/bin/env bash
# NebulaOps v23.3 — verify installed extensions own local Kubernetes manifests.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"
for ext in apiforge kubebridge contract-hub; do
  manifest="extensions/$ext/k8s/deployment.yml"
  [ -f "$manifest" ] || { log_err "missing local Kubernetes manifest: $manifest"; exit 1; }
  grep -q '^kind: Deployment$' "$manifest" || { log_err "$manifest does not define a Deployment"; exit 1; }
  grep -q '^kind: Service$' "$manifest" || { log_err "$manifest does not define a Service"; exit 1; }
  grep -q '^kind: Namespace$' "$manifest" || { log_err "$manifest does not define the namespace"; exit 1; }
  grep -q '^  replicas: 0$' "$manifest" || { log_err "$manifest must be disabled by default with replicas: 0"; exit 1; }
  python3 scripts/validate-yaml.py "$manifest"
done
log_ok "Installed extension-local Kubernetes manifests are present and valid"
