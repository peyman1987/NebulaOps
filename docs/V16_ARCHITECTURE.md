# NebulaOps v16 Architecture

NebulaOps v16 is a personal-machine platform engineering lab. It is intentionally advanced but still executable on one
WSL/Linux machine.

## Layers

1. **Experience layer** — Angular 18 spatial dashboard with a 3D animated service galaxy, realtime operations tabs and
   control links to service dashboards.
2. **Gateway layer** — Spring Boot gateway exposing safe local endpoints for Docker, Kubernetes, Helm and Grafana
   operations.
3. **Domain services** — auth, task, file and notification Spring Boot services backed by MongoDB.
4. **Go runtime services** — cache API and event worker connected to Redis and RabbitMQ.
5. **Data plane** — MongoDB, Redis and RabbitMQ with management UIs.
6. **Orchestration plane** — Docker Engine, Docker Compose, kubectl and Helm, without Docker Desktop.
7. **Observability plane** — Prometheus scraping and Grafana dashboards with fixed provisioning.

## Frontend features

- 3D hero topology with interactive planets for Docker, Kubernetes, Helm, Grafana and SLOs.
- Local service map where each service opens its real control panel or navigates to the internal operational tab.
- Docker Desktop-like inventory and container actions.
- OpenLens-like Kubernetes resource console with YAML editor and scale controls.
- Helm release lifecycle panel.
- Grafana health and dashboard panel.
- Observability stream with auto-refresh logs.
- GitLab/ArgoCD pipeline visualization.
- DevSecOps policy checklist.

## Performance strategy

- Static Angular build served by Nginx.
- Gateway is the only UI-facing API boundary.
- Infrastructure services are isolated in the Docker network `nebulaops-network`.
- Prometheus scraping uses local DNS service names, avoiding host routing issues.
- WSL scripts preflight native Docker, Compose, kubectl and Helm before starting.

## Runbook

```bash
./scripts/wsl/start.sh
./scripts/wsl/status.sh
./scripts/wsl/smoke-test.sh
./scripts/wsl/logs.sh grafana
```

## v16 functional 3D diagrams

The SVGs were rebuilt as technical flow diagrams. Start with `docs/V16_DIAGRAM_GUIDE.md`, then open:

- `docs/diagrams/request-flow-sequence.svg`
- `docs/diagrams/runtime-architecture.svg`
- `docs/diagrams/observability-grafana-flow.svg`
- `docs/diagrams/messaging-cache-flow.svg`
- `docs/diagrams/frontend-operations-dashboard.svg`

Each diagram uses cyan arrows for API control, purple dashed arrows for async events and green dotted arrows for
metrics/logs.
