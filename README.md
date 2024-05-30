# NebulaOps v17 — Professional DevOps Control Center

NebulaOps v17 is a local-first DevOps SaaS portfolio platform designed for WSL Ubuntu and Linux workstations. It
demonstrates a complete platform-engineering stack with an Angular operations dashboard, Spring Boot microservices, Go
services, MongoDB, Redis, RabbitMQ, Docker Engine control, Kubernetes operations, Helm release management, Prometheus
and Grafana.

The project is intentionally executable on a single personal machine while still presenting the architecture,
documentation and UI quality expected from a senior cloud/platform engineering portfolio.

## Core capabilities

- Angular control center with a professional dark SaaS interface and animated infrastructure views.
- Docker runtime operations: containers, images, stats and lifecycle actions.
- Kubernetes console: workloads, services, pods, namespaces, logs and live YAML editing.
- Helm release inventory and basic release operations through the gateway.
- Spring Cloud Gateway as the API entry point for backend capabilities.
- Java 21 Spring Boot services for auth, tasks, files and notifications.
- Go workers and cache services for lightweight runtime components.
- MongoDB persistence, Redis caching and RabbitMQ event delivery.
- Prometheus metrics collection and Grafana dashboards.
- WSL/Linux scripts for setup, startup, shutdown and validation.

## Quick start on WSL Ubuntu

```bash
cd nebulaops-v17
./scripts/wsl/install-native-toolchain.sh
./scripts/wsl/start.sh
./scripts/wsl/smoke-test.sh
```

## Local URLs

| Component           | URL                                   | Credentials   |
|---------------------|---------------------------------------|---------------|
| Frontend            | http://localhost:4200                 | admin / admin |
| Gateway health      | http://localhost:8080/actuator/health | none          |
| Grafana             | http://localhost:3000                 | admin / admin |
| Prometheus          | http://localhost:9090                 | none          |
| Mongo Express       | http://localhost:8088                 | admin / admin |
| Redis Commander     | http://localhost:8089                 | admin / admin |
| RabbitMQ Management | http://localhost:15672                | guest / guest |

## Stop the platform

```bash
./scripts/wsl/stop.sh
```

Docker volumes are preserved by default.

## Architecture diagrams

The SVG diagrams were rebuilt as professional technical assets with consistent English labels, dark SaaS styling,
animated request paths and clear responsibilities. Start here:

- `docs/diagrams/v17-saas-flow-3d.svg`
- `docs/diagrams/v17-runtime-control-3d.svg`
- `docs/diagrams/runtime-architecture.svg`
- `docs/diagrams/request-flow-sequence.svg`
- `docs/diagrams/kubernetes-helm-view.svg`
- `docs/diagrams/observability-grafana-flow.svg`
- `docs/diagrams/gitlab-argocd-flow.svg`

## Grafana provisioning note

Grafana must contain exactly one default datasource. The expected datasource is Prometheus:

```yaml
name: Prometheus
uid: prometheus
url: http://prometheus:9090
isDefault: true
```

Do not add another datasource with `isDefault: true`, otherwise Grafana can restart with a provisioning error.

## Project positioning

NebulaOps is designed to demonstrate practical DevOps and platform engineering skills: application delivery, local
cloud-native tooling, observability, container orchestration, service boundaries, event-driven components and
operational documentation.
