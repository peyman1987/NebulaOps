# OpenLens-like Kubernetes Console

This module provides a operations-grade Kubernetes operations console integrated into the NebulaOps frontend.

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


## v23.3 multi-cluster kubeconfig registry

OpenLens Kubernetes now supports additional clusters through a MongoDB-backed kubeconfig registry. Operators can paste a kubeconfig in the OpenLens UI; the gateway stores it in MongoDB and creates a dedicated cluster tab. All live operations use the selected `clusterId` and run against that kubeconfig: pods, deployments, services, ingress, configmaps, statefulsets, nodes, namespaces, logs, describe, YAML diff/apply/delete, restart, scale, cordon, uncordon and drain.

New endpoints:

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/kubernetes/kubeconfigs` | List local context and MongoDB-saved kubeconfigs |
| POST | `/api/kubernetes/kubeconfigs` | Save kubeconfig YAML into MongoDB |
| DELETE | `/api/kubernetes/kubeconfigs/{id}` | Remove a saved kubeconfig |
| POST | `/api/kubernetes/kubeconfigs/{id}/probe` | Probe selected cluster |
| GET | `/api/kubernetes/helm/releases?clusterId={id}` | List Helm releases for the selected cluster |

Kubernetes API calls accept `clusterId={id}`. Without `clusterId`, the gateway uses the mounted local `kubectl` current context.
