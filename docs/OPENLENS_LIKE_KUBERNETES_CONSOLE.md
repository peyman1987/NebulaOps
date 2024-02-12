# OpenLens-like Kubernetes Console

This module provides a portfolio-grade Kubernetes operations console integrated into the NebulaOps frontend.

## Current implementation

The console is no longer mock-only. Kubernetes actions are sent to the gateway service and executed with `kubectl`
against the configured cluster.

### Implemented capabilities

- List Namespaces, Deployments, ReplicaSets, Services, Ingresses, ConfigMaps and Secrets.
- Open a resource and load its live YAML from the cluster.
- Edit YAML and apply it with `kubectl apply -f -`.
- Create resources from the UI form.
- Delete resources from the UI.
- Scale Deployments and ReplicaSets up or down from the UI.
- Show Kubernetes logs where NebulaOps labels are available.
- Show backend errors in the UI instead of silently falling back to local mock data.

## API contract

| Method | Path                                   | Purpose                              |
|--------|----------------------------------------|--------------------------------------|
| GET    | `/api/kubernetes/snapshot`             | Cluster summary, resources, logs     |
| GET    | `/api/kubernetes/resources`            | Filterable resource list             |
| GET    | `/api/kubernetes/resources/{id}`       | Live resource detail and YAML        |
| POST   | `/api/kubernetes/resources`            | Create/apply generated resource YAML |
| POST   | `/api/kubernetes/yaml/apply`           | Apply editor YAML                    |
| PATCH  | `/api/kubernetes/resources/{id}/scale` | Scale workload replicas              |
| DELETE | `/api/kubernetes/resources/{id}`       | Delete a resource                    |

## Required local setup

```bash
kubectl config current-context
kubectl get nodes
docker compose -f infrastructure/docker-compose.yml build --no-cache gateway-service frontend
docker compose -f infrastructure/docker-compose.yml up -d
```

The gateway service must have access to a valid kubeconfig mounted at `/kube/config`.
