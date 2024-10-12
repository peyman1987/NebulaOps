# NebulaOps v19.4 — INFRA Hub

The `INFRA` tab is restored as a dedicated console and feature-access hub.

## External consoles

| Console             | URL                      | Purpose                                               |
|---------------------|--------------------------|-------------------------------------------------------|
| Grafana             | `http://localhost:3000`  | Dashboards, Loki logs and runtime metrics             |
| Redis Commander     | `http://localhost:8089`  | Redis keys, cache inspection and commands             |
| Mongo Express       | `http://localhost:8088`  | MongoDB collections, documents and indexes            |
| RabbitMQ Management | `http://localhost:15672` | Queues, exchanges, bindings and consumers             |
| Prometheus          | `http://localhost:9090`  | Metrics queries, scrape targets and service discovery |
| Gateway API         | `http://localhost:8080`  | Public API entrypoint                                 |
| Pipeline Engine API | `http://localhost:8087`  | CI/CD Pipeline Designer backend                       |

## Internal feature shortcuts

- Kubernetes Visual Cluster
- CI/CD Pipeline Designer
- ArgoCD integration gates inside CI/CD Designer
- Observability
- DevSecOps
- AI OPS
- FinOps
- Backups

This keeps the home page clean while preserving the older NebulaOps navigation pattern where all infrastructure tools
are reachable from one tab.
