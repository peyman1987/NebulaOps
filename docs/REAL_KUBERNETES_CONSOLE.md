# Real Kubernetes Console Implementation

NebulaOps now exposes Kubernetes operations through the gateway service instead of using static or mocked data in the
frontend.

## Runtime model

The Angular frontend calls the gateway under `/api/kubernetes/*`. The gateway executes `kubectl` inside the container
using the mounted kubeconfig at `/kube/config`.

```text
Angular UI -> Nginx /api proxy -> gateway-service -> kubectl -> Kubernetes API Server
```

## Supported operations

| UI capability             | API endpoint                                 | Backend action                                          |
|---------------------------|----------------------------------------------|---------------------------------------------------------|
| Cluster/resource snapshot | `GET /api/kubernetes/snapshot`               | `kubectl get ... -A -o json`                            |
| Resource detail and YAML  | `GET /api/kubernetes/resources/{id}`         | `kubectl get <kind> <name> -n <namespace> -o yaml/json` |
| Create resource           | `POST /api/kubernetes/resources`             | generates YAML then `kubectl apply -f -`                |
| Apply YAML editor changes | `POST /api/kubernetes/yaml/apply`            | `kubectl apply -f -`                                    |
| Delete resource           | `DELETE /api/kubernetes/resources/{id}`      | `kubectl delete <kind> <name>`                          |
| Scale workload            | `PATCH /api/kubernetes/resources/{id}/scale` | `kubectl scale <kind>/<name> --replicas=N`              |
| Logs                      | included in snapshot                         | `kubectl logs ... --tail=60`                            |

## Kubeconfig mount

`infrastructure/docker-compose.yml` mounts the host kubeconfig into the gateway container:

```yaml
gateway-service:
  environment:
    KUBECONFIG: /kube/config
  volumes:
    - ${HOME}/.kube:/kube:ro
```

Before starting NebulaOps, verify that kubectl works on the host:

```bash
kubectl config current-context
kubectl get nodes
```

Then rebuild the gateway:

```bash
docker compose -f infrastructure/docker-compose.yml build --no-cache gateway-service frontend
docker compose -f infrastructure/docker-compose.yml up -d
```

## Notes for Docker Desktop / WSL

For Docker Desktop Kubernetes, the kubeconfig usually points to `kubernetes.docker.internal`, which is reachable from
containers. If your kubeconfig points to `127.0.0.1`, replace the server address with a hostname reachable from the
gateway container, or use a kubeconfig dedicated to container access.

## Safety

The console performs real cluster mutations. Apply RBAC restrictions in non-local environments. For portfolio and local
demos, use a local Docker Desktop, kind, minikube or k3d cluster.
