# Frontend Operations Dashboard

NebulaOps includes an Angular operations dashboard designed for portfolio demonstrations and local engineering
workflows. The frontend is no longer a simple static Kanban board: it now presents platform state, Kubernetes runtime
resources, service logs and a persistent task workflow.

## Functional scope

The dashboard exposes four operational areas:

| Area                 | Description                                                                                 |
|----------------------|---------------------------------------------------------------------------------------------|
| Executive metrics    | Total tasks, completed work, critical work, running pods, service count and namespace count |
| Kubernetes overview  | Cluster status, provider, version, node count, CPU and memory utilization                   |
| Kubernetes inventory | Namespaces, pods, service discovery entries, readiness, restarts and resource usage         |
| Microservice logs    | Gateway, task-service, notification-service, Go cache service and file-service log stream   |
| Task workflow        | Drag-and-drop task board with persistence and backend synchronization                       |

## Task persistence behavior

Task movement is persisted in two layers:

1. **Backend persistence**: when tasks come from `/api/tasks`, moving a card
   calls `PATCH /api/tasks/{id}/status/{status}`. The task-service saves the new state in MongoDB and publishes
   a `TaskStatusChanged` event to RabbitMQ.
2. **Local fallback**: if the backend is unavailable during a demo, the board writes to `localStorage` so moved cards do
   not reset after a page refresh.

This gives a stable demo experience while still proving real backend persistence when the platform is running through
Docker Compose or Kubernetes.

## API routing from the frontend container

The production frontend image includes an Nginx reverse proxy configuration:

```nginx
location /api/ {
  proxy_pass http://gateway-service:8080/api/;
}
```

This is important because browser calls to `/api/tasks` must reach Spring Cloud Gateway instead of being handled by the
static Nginx server.

## Kubernetes data source

The current implementation ships with `src/assets/k8s-snapshot.json` as a deterministic demo data source for portfolio
presentations. It models namespaces, pods, services and logs for the optimized architecture without Kafka.

For a live cluster implementation, replace the static asset with a backend endpoint such
as `/api/platform/kubernetes/snapshot`. That backend should authenticate requests, use a service account with read-only
Kubernetes RBAC and expose sanitized summaries rather than raw cluster secrets.

Recommended read-only permissions:

```yaml
resources: ["namespaces", "pods", "services", "deployments", "replicasets", "events"]
verbs: ["get", "list", "watch"]
```

## Operational design principles

- The UI avoids exposing sensitive Secret values.
- Logs are displayed as sanitized operational entries, not raw credentials or tokens.
- Kubernetes objects are grouped by namespace to match real platform operations.
- Task state is synchronized optimistically and rolled back if backend persistence fails.
- The frontend remains useful in local/offline demos through local fallback data.
