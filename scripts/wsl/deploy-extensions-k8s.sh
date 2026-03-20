#!/usr/bin/env bash
# NebulaOps v23.1 — Build and deploy all platform extensions directly to Kubernetes.
# start.sh --rebuild path: Docker Compose services + Kubernetes extensions.
# k3s/containerd safe: uses a local Docker registry instead of fragile ctr image imports.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

ALL_EXTENSIONS=(apiforge kubebridge contract-hub)
SELECTED_EXTENSIONS=("${ALL_EXTENSIONS[@]}")
K8S_NAMESPACE="${NEBULAOPS_EXTENSIONS_NAMESPACE:-nebulaops}"
SKIP_BUILD=false
SKIP_IMAGE_LOAD=false

# For k3s/containerd the Docker daemon image store is not the Kubernetes image store.
# Instead of importing tar archives with ctr, publish extension images to a local registry
# and patch the applied manifest image to localhost:<port>/<image>:latest.
LOCAL_REGISTRY_HOST="${NEBULAOPS_LOCAL_REGISTRY_HOST:-localhost}"
LOCAL_REGISTRY_PORT="${NEBULAOPS_LOCAL_REGISTRY_PORT:-5001}"
LOCAL_REGISTRY="${NEBULAOPS_LOCAL_REGISTRY:-${LOCAL_REGISTRY_HOST}:${LOCAL_REGISTRY_PORT}}"
LOCAL_REGISTRY_CONTAINER="${NEBULAOPS_LOCAL_REGISTRY_CONTAINER:-nebulaops-v23-1-registry}"
EXTENSIONS_IMAGE_MODE="${NEBULAOPS_EXTENSIONS_IMAGE_MODE:-auto}" # auto | registry | runtime
PLATFORM="${NEBULAOPS_EXTENSIONS_PLATFORM:-linux/amd64}"

usage() {
  cat <<USAGE
Usage: $0 [--skip-build] [--skip-image-load] [--only <extension>] [--image-mode auto|registry|runtime]

Builds extension Docker images, makes them visible to Kubernetes, applies each
extension-local manifest and waits for rollout.

Image modes:
  auto      Use the best mode for the current Kubernetes context. k3s/containerd uses a local registry. Default.
  registry  Always publish extension images to localhost:${LOCAL_REGISTRY_PORT} and deploy using registry image names.
  runtime   Use runtime-specific loading only: Docker Desktop, kind, minikube, k3d. Not recommended for k3s.

Available extensions:
  apiforge kubebridge contract-hub

Examples:
  ./scripts/wsl/deploy-extensions-k8s.sh
  ./scripts/wsl/deploy-extensions-k8s.sh --only apiforge
  NEBULAOPS_EXTENSIONS_IMAGE_MODE=registry ./scripts/wsl/deploy-extensions-k8s.sh
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --skip-build) SKIP_BUILD=true; shift ;;
    --skip-image-load) SKIP_IMAGE_LOAD=true; shift ;;
    --image-mode)
      [ "$#" -ge 2 ] || { log_err "--image-mode requires auto, registry or runtime"; exit 1; }
      EXTENSIONS_IMAGE_MODE="$2"
      shift 2
      ;;
    --only)
      [ "$#" -ge 2 ] || { log_err "--only requires an extension slug"; exit 1; }
      SELECTED_EXTENSIONS=("$2")
      shift 2
      ;;
    -h|--help) usage; exit 0 ;;
    *) log_err "Unknown argument: $1"; usage; exit 1 ;;
  esac
done

case "$EXTENSIONS_IMAGE_MODE" in
  auto|registry|runtime) ;;
  *) log_err "Invalid image mode: $EXTENSIONS_IMAGE_MODE"; usage; exit 1 ;;
esac

contains_extension() {
  local needle="$1" item
  for item in "${ALL_EXTENSIONS[@]}"; do [ "$item" = "$needle" ] && return 0; done
  return 1
}

image_for() {
  case "$1" in
    apiforge) echo "nebulaops-v23-1-apiforge:latest" ;;
    kubebridge) echo "nebulaops-v23-1-kubebridge:latest" ;;
    runbook-center) echo "nebulaops-v23-1-runbook-center:latest" ;;
    extension-registry) echo "nebulaops-v23-1-extension-registry:latest" ;;
    contract-hub) echo "nebulaops-v23-1-contract-hub:latest" ;;
    eventops-center) echo "nebulaops-v23-1-eventops-center:latest" ;;
    observability-lens) echo "nebulaops-v23-1-observability-lens:latest" ;;
    gitops-center) echo "nebulaops-v23-1-gitops-center:latest" ;;
    secrets-config-center) echo "nebulaops-v23-1-secrets-config-center:latest" ;;
    slo-center) echo "nebulaops-v23-1-slo-center:latest" ;;
    backup-recovery-center) echo "nebulaops-v23-1-backup-recovery-center:latest" ;;
    *) return 1 ;;
  esac
}

context_for() {
  case "$1" in
    apiforge) echo "./extensions/apiforge" ;;
    kubebridge) echo "./extensions/kubebridge" ;;
    runbook-center) echo "./extensions/runbook-center" ;;
    extension-registry) echo "./extensions/extension-registry" ;;
    contract-hub) echo "./extensions/contract-hub" ;;
    eventops-center) echo "./extensions/eventops-center" ;;
    observability-lens) echo "./extensions/observability-lens" ;;
    gitops-center) echo "./extensions/gitops-center" ;;
    secrets-config-center) echo "./extensions/secrets-config-center" ;;
    slo-center) echo "./extensions/slo-center" ;;
    backup-recovery-center) echo "./extensions/backup-recovery-center" ;;
    *) return 1 ;;
  esac
}

manifest_for() {
  local ext="$1"
  local local_manifest="extensions/${ext}/k8s/deployment.yml"
  if [ -f "$local_manifest" ]; then
    echo "$local_manifest"
    return 0
  fi
  case "$ext" in
    apiforge) echo "infrastructure/kubernetes/apiforge.yaml" ;;
    kubebridge) echo "infrastructure/kubernetes/extensions/kubebridge.yaml" ;;
    runbook-center) echo "infrastructure/kubernetes/extensions/runbook-center.yaml" ;;
    extension-registry) echo "infrastructure/kubernetes/extensions/extension-registry.yaml" ;;
    contract-hub) echo "infrastructure/kubernetes/extensions/contract-hub.yaml" ;;
    eventops-center) echo "infrastructure/kubernetes/extensions/eventops-center.yaml" ;;
    observability-lens) echo "infrastructure/kubernetes/extensions/observability-lens.yaml" ;;
    gitops-center) echo "infrastructure/kubernetes/extensions/gitops-center.yaml" ;;
    secrets-config-center) echo "infrastructure/kubernetes/extensions/secrets-config-center.yaml" ;;
    slo-center) echo "infrastructure/kubernetes/extensions/slo-center.yaml" ;;
    backup-recovery-center) echo "infrastructure/kubernetes/extensions/backup-recovery-center.yaml" ;;
    *) return 1 ;;
  esac
}

port_for() {
  case "$1" in
    apiforge) echo "31110" ;;
    kubebridge) echo "31111" ;;
    runbook-center) echo "31112" ;;
    extension-registry) echo "31113" ;;
    contract-hub) echo "31114" ;;
    eventops-center) echo "31115" ;;
    observability-lens) echo "31116" ;;
    gitops-center) echo "31117" ;;
    secrets-config-center) echo "31118" ;;
    slo-center) echo "31119" ;;
    backup-recovery-center) echo "31120" ;;
    *) return 1 ;;
  esac
}

url_for() {
  local slug="$1" port="$2"
  case "$slug" in
    apiforge) echo "http://localhost:${port}/apiforge/" ;;
    kubebridge) echo "http://localhost:${port}/kubebridge/" ;;
    runbook-center) echo "http://localhost:${port}/runbook-center/" ;;
    extension-registry) echo "http://localhost:${port}/extension-registry/" ;;
    contract-hub) echo "http://localhost:${port}/contract-hub/" ;;
    eventops-center) echo "http://localhost:${port}/eventops-center/" ;;
    observability-lens) echo "http://localhost:${port}/observability-lens/" ;;
    gitops-center) echo "http://localhost:${port}/gitops-center/" ;;
    secrets-config-center) echo "http://localhost:${port}/secrets-config-center/" ;;
    slo-center) echo "http://localhost:${port}/slo-center/" ;;
    backup-recovery-center) echo "http://localhost:${port}/backup-recovery-center/" ;;
    *) return 1 ;;
  esac
}

health_url_for() {
  local slug="$1" port="$2"
  case "$slug" in
    apiforge) echo "http://localhost:${port}/apiforge/actuator/health" ;;
    *) echo "http://localhost:${port}/healthz" ;;
  esac
}

current_context() { kubectl config current-context 2>/dev/null || true; }
node_runtime() { kubectl get nodes -o jsonpath='{.items[0].status.nodeInfo.containerRuntimeVersion}' 2>/dev/null || true; }

is_docker_desktop_context() { printf '%s' "$(current_context)" | grep -Eq '(^|/)docker-desktop$'; }
is_kind_context() { command -v kind >/dev/null 2>&1 && printf '%s' "$(current_context)" | grep -q '^kind-'; }
is_minikube_context() { command -v minikube >/dev/null 2>&1 && printf '%s' "$(current_context)" | grep -q 'minikube'; }
is_k3d_context() { command -v k3d >/dev/null 2>&1 && printf '%s' "$(current_context)" | grep -qi 'k3d'; }

should_use_local_registry() {
  case "$EXTENSIONS_IMAGE_MODE" in
    registry) return 0 ;;
    runtime) return 1 ;;
  esac

  # Docker Desktop, kind, minikube and k3d have their own reliable image loading paths.
  is_docker_desktop_context && return 1
  is_kind_context && return 1
  is_minikube_context && return 1
  is_k3d_context && return 1

  # k3s and many standalone WSL clusters use containerd; Docker-built images are not visible there.
  printf '%s' "$(node_runtime)" | grep -qi 'containerd'
}

ensure_local_registry() {
  command -v docker >/dev/null 2>&1 || { log_err "docker is required to run the local extension registry"; return 1; }
  docker info >/dev/null 2>&1 || { log_err "Docker daemon is not reachable; cannot run local extension registry"; return 1; }

  if docker ps --format '{{.Names}}' | grep -qx "$LOCAL_REGISTRY_CONTAINER"; then
    log_info "Local extension registry is already running: ${LOCAL_REGISTRY}"
  elif docker ps -a --format '{{.Names}}' | grep -qx "$LOCAL_REGISTRY_CONTAINER"; then
    log_step "Starting local extension registry: ${LOCAL_REGISTRY_CONTAINER} (${LOCAL_REGISTRY})"
    docker start "$LOCAL_REGISTRY_CONTAINER" >/dev/null
  else
    log_step "Creating local extension registry: ${LOCAL_REGISTRY_CONTAINER} (${LOCAL_REGISTRY})"
    docker run -d --restart unless-stopped -p "${LOCAL_REGISTRY_PORT}:5000" --name "$LOCAL_REGISTRY_CONTAINER" registry:2 >/dev/null
  fi

  for _ in $(seq 1 30); do
    if curl -fsS "http://${LOCAL_REGISTRY}/v2/" >/dev/null 2>&1; then
      log_ok "Local extension registry ready: http://${LOCAL_REGISTRY}/v2/"
      return 0
    fi
    sleep 1
  done
  log_err "Local registry did not become reachable at http://${LOCAL_REGISTRY}/v2/"
  return 1
}

registry_image_for() {
  local image="$1"
  echo "${LOCAL_REGISTRY}/${image}"
}

build_extension_image() {
  local image="$1" context="$2" registry_image="${3:-}"
  local tags=(-t "$image")
  [ -n "$registry_image" ] && tags+=(-t "$registry_image")

  if docker buildx version >/dev/null 2>&1; then
    if docker buildx build --load --platform "$PLATFORM" --provenance=false --sbom=false "${tags[@]}" "$context"; then
      return 0
    fi
    log_warn "docker buildx build failed for $image; falling back to classic docker build"
  fi
  DOCKER_BUILDKIT=0 docker build --platform "$PLATFORM" "${tags[@]}" "$context"
}

load_image_into_cluster_runtime() {
  local image="$1" ext="$2"
  [ "$SKIP_IMAGE_LOAD" = "true" ] && { log_info "Skipping image load for $ext"; return 0; }

  local ctx runtime k3d_cluster
  ctx="$(current_context)"
  runtime="$(node_runtime)"

  if is_docker_desktop_context; then
    log_info "Kubernetes context is docker-desktop; Docker-built image is visible to the cluster: $image"
    return 0
  fi

  if is_kind_context; then
    local cluster="${ctx#kind-}"
    log_step "Loading $ext image into kind cluster: $cluster"
    kind load docker-image "$image" --name "$cluster"
    return 0
  fi

  if is_minikube_context; then
    log_step "Loading $ext image into minikube"
    minikube image load "$image"
    return 0
  fi

  if command -v k3d >/dev/null 2>&1; then
    k3d_cluster="$(k3d cluster list -o json 2>/dev/null | python3 -c 'import json,sys; data=json.load(sys.stdin); print(data[0]["name"] if data else "")' 2>/dev/null || true)"
    if [ -n "$k3d_cluster" ] && (printf '%s' "$ctx" | grep -qi 'k3d' || printf '%s' "$ctx" | grep -q "$k3d_cluster"); then
      log_step "Importing $ext image into k3d cluster: $k3d_cluster"
      k3d image import "$image" -c "$k3d_cluster"
      return 0
    fi
  fi

  log_warn "No direct runtime image loader matched for context '$ctx' (runtime: ${runtime:-unknown})."
  log_warn "Use --image-mode registry for k3s/containerd, or set NEBULAOPS_EXTENSIONS_IMAGE_MODE=registry."
  return 1
}

publish_to_local_registry() {
  local registry_image="$1"
  log_step "Pushing extension image to local registry: $registry_image"
  docker push "$registry_image"
}

apply_manifest() {
  local ext="$1" manifest="$2" image="$3" deployed_image="$4" use_registry_manifest="$5"
  if [ "$use_registry_manifest" = "true" ]; then
    local tmp_manifest
    tmp_manifest="$(mktemp "/tmp/nebulaops-${ext}-manifest-XXXXXX.yml")"
    python3 - "$manifest" "$tmp_manifest" "$image" "$deployed_image" <<'PY'
import sys
src, dst, original, replacement = sys.argv[1:5]
text = open(src, encoding='utf-8').read()
text = text.replace(f"image: {original}", f"image: {replacement}")
text = text.replace("imagePullPolicy: IfNotPresent", "imagePullPolicy: Always")
open(dst, 'w', encoding='utf-8').write(text)
PY
    log_step "Applying Kubernetes manifest for $ext using local registry image: $deployed_image"
    kubectl apply -f "$tmp_manifest"
    rm -f "$tmp_manifest"
  else
    log_step "Applying Kubernetes manifest for $ext: $manifest"
    kubectl apply -f "$manifest"
  fi
}

for ext in "${SELECTED_EXTENSIONS[@]}"; do
  contains_extension "$ext" || { log_err "Unknown extension: $ext"; usage; exit 1; }
done

DOCKER_AVAILABLE=false
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  DOCKER_AVAILABLE=true
fi
if [ "$SKIP_BUILD" != "true" ] && [ "$DOCKER_AVAILABLE" != "true" ]; then
  log_err "docker is required to build extension images. Start Docker or use --skip-build with images already available to the cluster."
  exit 1
fi
if ! command -v kubectl >/dev/null 2>&1; then
  log_err "kubectl is required to deploy NebulaOps extensions to Kubernetes"
  exit 1
fi
if ! kubectl cluster-info >/dev/null 2>&1; then
  log_err "No Kubernetes cluster is reachable from kubectl. Start/select a valid context."
  exit 1
fi

USE_LOCAL_REGISTRY=false
if should_use_local_registry; then
  USE_LOCAL_REGISTRY=true
  log_info "Kubernetes runtime is '${EXTENSIONS_IMAGE_MODE}' mode over $(node_runtime). Using local registry ${LOCAL_REGISTRY}."
  [ "$SKIP_IMAGE_LOAD" = "true" ] || ensure_local_registry
fi

if [ -f "infrastructure/kubernetes/namespace.yaml" ]; then
  log_step "Ensuring Kubernetes namespace exists: $K8S_NAMESPACE"
  kubectl apply -f infrastructure/kubernetes/namespace.yaml
else
  kubectl get namespace "$K8S_NAMESPACE" >/dev/null 2>&1 || kubectl create namespace "$K8S_NAMESPACE"
fi

for ext in "${SELECTED_EXTENSIONS[@]}"; do
  image="$(image_for "$ext")"
  context="$(context_for "$ext")"
  manifest="$(manifest_for "$ext")"
  deployed_image="$image"
  registry_image=""

  [ -d "$context" ] || { log_err "Extension context not found: $context"; exit 1; }
  [ -f "$manifest" ] || { log_err "Kubernetes manifest not found for $ext: $manifest"; exit 1; }

  if [ "$USE_LOCAL_REGISTRY" = "true" ]; then
    registry_image="$(registry_image_for "$image")"
    deployed_image="$registry_image"
  fi

  if [ "$SKIP_BUILD" != "true" ]; then
    log_step "Building $ext image: $image"
    build_extension_image "$image" "$context" "$registry_image"

    if [ "$USE_LOCAL_REGISTRY" = "true" ]; then
      [ "$SKIP_IMAGE_LOAD" = "true" ] || publish_to_local_registry "$registry_image"
    else
      load_image_into_cluster_runtime "$image" "$ext"
    fi
  else
    log_info "Skipping Docker build for $ext"
    if [ "$USE_LOCAL_REGISTRY" = "true" ]; then
      log_warn "With --skip-build in registry mode, make sure $deployed_image already exists in the local registry."
    else
      log_warn "With --skip-build, make sure $image already exists inside the Kubernetes runtime, not only in Docker."
    fi
  fi

  apply_manifest "$ext" "$manifest" "$image" "$deployed_image" "$USE_LOCAL_REGISTRY"
  log_step "Enabling extension deployment: $ext replicas=1"
  kubectl -n "$K8S_NAMESPACE" scale "deployment/$ext" --replicas=1
  kubectl -n "$K8S_NAMESPACE" rollout restart "deployment/$ext" >/dev/null 2>&1 || true

  log_step "Waiting for rollout: $ext"
  if ! kubectl -n "$K8S_NAMESPACE" rollout status "deployment/$ext" --timeout=300s; then
    log_warn "Rollout did not complete for $ext. Current resources:"
    kubectl -n "$K8S_NAMESPACE" get deploy,pods,svc -l app="$ext" -o wide || true
    kubectl -n "$K8S_NAMESPACE" describe deploy "$ext" || true
    kubectl -n "$K8S_NAMESPACE" describe pod -l app="$ext" || true
    kubectl -n "$K8S_NAMESPACE" get events --sort-by=.lastTimestamp | tail -80 || true
    if [ "$USE_LOCAL_REGISTRY" = "true" ]; then
      log_err "The extension was deployed with image $deployed_image. Check that k3s can pull http://${LOCAL_REGISTRY}/v2/."
      log_err "Manual check: curl -fsS http://${LOCAL_REGISTRY}/v2/ && kubectl -n $K8S_NAMESPACE describe pod -l app=$ext"
    fi
    exit 1
  fi

  kubectl -n "$K8S_NAMESPACE" get pods -l app="$ext" -o wide || true
  port="$(port_for "$ext")"
  health_url="$(health_url_for "$ext" "$port")"
  public_url="$(url_for "$ext" "$port")"
  log_step "Checking $ext health: $health_url"
  healthy=false
  for _ in $(seq 1 60); do
    if curl -fsS "$health_url" >/dev/null 2>&1; then healthy=true; break; fi
    sleep 1
  done
  if [ "$healthy" = "true" ]; then
    log_ok "$ext is reachable: $public_url"
  else
    log_warn "$ext deployed, but NodePort health did not become reachable from localhost within 60s. Inspect with: kubectl -n $K8S_NAMESPACE get pods,svc -l app=$ext -o wide"
  fi
done

log_step "Final NebulaOps extension resources in namespace: $K8S_NAMESPACE"
kubectl -n "$K8S_NAMESPACE" get pods,deploy,svc -l app.kubernetes.io/part-of=nebulaops -o wide || true
log_ok "NebulaOps extension deployment completed"
