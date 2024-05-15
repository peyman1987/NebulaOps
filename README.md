# NebulaOps v16 — Spatial DevOps SaaS Platform

NebulaOps v16 is a local-first, portfolio-grade DevOps SaaS platform designed for WSL and Linux without Docker Desktop.
It combines an Angular 3D control center, Spring Boot microservices, Go workers, MongoDB, Redis, RabbitMQ, Docker Engine
control, Kubernetes/OpenLens-like operations, Helm release management, Prometheus and Grafana.

## What changed in v16

- New 3D spatial frontend with animated cloud topology, service galaxy and SaaS-style control panels.
- Real Docker controls from the UI: list containers, images, stats, start, stop, restart and remove.
- Kubernetes console for resources, live YAML editing, scale actions, logs and local snapshots.
- Helm release inventory and uninstall workflow through the gateway.
- Grafana fixed with exactly one default datasource to avoid provisioning restart loops.
- Prometheus + Grafana provisioning for local observability.
- WSL/Linux scripts with native Docker Engine, Docker Compose, kubectl and Helm.
- Modern architecture diagrams and animated SVG documentation.

## Quick start on WSL Ubuntu

```bash
cd nebulaops-v16
./scripts/wsl/install-native-toolchain.sh
./scripts/wsl/start.sh
./scripts/wsl/smoke-test.sh
```

URLs:

- Frontend: http://localhost:4200
- Gateway: http://localhost:8080/actuator/health
- Grafana: http://localhost:3000 — admin/admin
- Prometheus: http://localhost:9090
- Mongo Express: http://localhost:8088 — admin/admin
- Redis Commander: http://localhost:8089 — admin/admin
- RabbitMQ: http://localhost:15672 — guest/guest

## Stop

```bash
./scripts/wsl/stop.sh
```

Volumes are preserved by default.

## Important Grafana note

The v16 provisioning intentionally contains one default datasource only:

```yaml
name: Prometheus
uid: prometheus
url: http://prometheus:9090
isDefault: true
```

Do not add another `isDefault: true`, otherwise Grafana will restart
with: `Only one datasource per organization can be marked as default`.

## v16 functional 3D diagrams

The SVGs were rebuilt as technical flow diagrams. Start with `docs/V16_DIAGRAM_GUIDE.md`, then open:

- `docs/diagrams/request-flow-sequence.svg`
- `docs/diagrams/runtime-architecture.svg`
- `docs/diagrams/observability-grafana-flow.svg`
- `docs/diagrams/messaging-cache-flow.svg`
- `docs/diagrams/frontend-operations-dashboard.svg`

Each diagram uses cyan arrows for API control, purple dashed arrows for async events and green dotted arrows for
metrics/logs.
