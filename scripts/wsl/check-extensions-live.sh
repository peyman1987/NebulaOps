#!/usr/bin/env bash
# NebulaOps v22.5 — verify extension pods, services and live endpoints after deployment.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"
K8S_NAMESPACE="${NEBULAOPS_EXTENSIONS_NAMESPACE:-nebulaops}"
EXTENSIONS=(apiforge kubebridge contract-hub)
port_for(){ case "$1" in apiforge) echo 31110;; kubebridge) echo 31111;; runbook-center) echo 31112;; extension-registry) echo 31113;; contract-hub) echo 31114;; eventops-center) echo 31115;; observability-lens) echo 31116;; gitops-center) echo 31117;; secrets-config-center) echo 31118;; slo-center) echo 31119;; backup-recovery-center) echo 31120;; esac; }
health_for(){ local e="$1" p="$2"; if [ "$e" = apiforge ]; then echo "http://localhost:${p}/apiforge/actuator/health"; else echo "http://localhost:${p}/healthz"; fi; }
live_for(){ local e="$1" p="$2"; if [ "$e" = apiforge ]; then echo "http://localhost:${p}/apiforge/api/live"; else echo "http://localhost:${p}/${e}/api/live"; fi; }
cap_for(){ local e="$1" p="$2"; if [ "$e" = apiforge ]; then echo "http://localhost:${p}/apiforge/api/capabilities"; else echo "http://localhost:${p}/${e}/api/capabilities"; fi; }
if ! command -v kubectl >/dev/null 2>&1; then log_err "kubectl not found"; exit 1; fi
log_step "Kubernetes context"
kubectl config current-context 2>/dev/null || true
log_step "NebulaOps namespace resources: $K8S_NAMESPACE"
kubectl get namespace "$K8S_NAMESPACE" >/dev/null 2>&1 || { log_err "Namespace $K8S_NAMESPACE not found. Run ./scripts/wsl/deploy-extensions-k8s.sh"; exit 1; }
kubectl -n "$K8S_NAMESPACE" get pods,deploy,svc -l app.kubernetes.io/part-of=nebulaops -o wide || true
printf '\n%-28s %-10s %-14s %-10s %-10s %s\n' "EXTENSION" "DEPLOY" "PODS" "HEALTH" "LIVE" "URL"
for e in "${EXTENSIONS[@]}"; do
  p="$(port_for "$e")"
  deploy="MISSING"
  pods="MISSING"
  if kubectl -n "$K8S_NAMESPACE" get deploy "$e" >/dev/null 2>&1; then
    ready="$(kubectl -n "$K8S_NAMESPACE" get deploy "$e" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)"
    desired="$(kubectl -n "$K8S_NAMESPACE" get deploy "$e" -o jsonpath='{.spec.replicas}' 2>/dev/null || true)"
    deploy="${ready:-0}/${desired:-0}"
    pods="$(kubectl -n "$K8S_NAMESPACE" get pods -l app="$e" --no-headers 2>/dev/null | awk '{print $3}' | paste -sd, -)"
    [ -n "$pods" ] || pods="PENDING"
  fi
  hurl="$(health_for "$e" "$p")"
  lurl="$(live_for "$e" "$p")"
  curl -fsS "$hurl" >/dev/null 2>&1 && h="OK" || h="DOWN"
  curl -fsS "$lurl" >/dev/null 2>&1 && l="OK" || l="DOWN"
  printf '%-28s %-10s %-14s %-10s %-10s %s\n' "$e" "$deploy" "$pods" "$h" "$l" "$(cap_for "$e" "$p")"
done
