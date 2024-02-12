# NebulaOps Platform

**Enterprise cloud-native portfolio platform** built to demonstrate senior-level architecture, implementation
discipline, local developer experience, observability and deployment readiness.

NebulaOps is a lean microservice platform designed for Docker Desktop, WSL2 and Kubernetes. The system uses RabbitMQ as
the single asynchronous messaging layer, MongoDB for service-owned persistence, Redis for cache-oriented workloads,
Spring Boot for core domain APIs, Go for lightweight infrastructure services, Angular for the user interface,
Prometheus/Grafana for observability and Helm/Argo CD for deployment workflows.

## Executive summary

NebulaOps models a realistic SaaS operations platform with a clean separation between user interaction, API edge
routing, domain services, asynchronous processing and platform services. The architecture intentionally avoids Kafka to
keep the local runtime stable and easier to operate while preserving a professional event-driven design through RabbitMQ
exchanges and queues.

## Operations Console Features

The Angular frontend now includes a professional OpenLens-inspired operations console:

- Task CRUD with MongoDB-backed persistence and local fallback.
- Kubernetes resource inventory for Namespaces, Deployments, ReplicaSets, Services, Ingresses, ConfigMaps and Secrets.
- Resource creation and deletion directly from the frontend.
- YAML viewer/editor with an Apply action.
- Workload replica scaling from the UI.
- Namespace and kind filters for fast operations.
- Microservice log panel for platform observability demos.

The Kubernetes API implementation is intentionally safe for a portfolio demo: it uses a gateway-backed local model that
mirrors the shape of real Kubernetes operations without mutating a real cluster. The API can be replaced with a
Kubernetes Java client or in-cluster agent for production.

See `docs/OPENLENS_LIKE_KUBERNETES_CONSOLE.md` for API details and the production hardening roadmap.

## Architecture at a glance

![Animated runtime architecture](docs/diagrams/runtime-architecture.svg)

| Layer           | Components                        | Responsibility                                                                                      |
|-----------------|-----------------------------------|-----------------------------------------------------------------------------------------------------|
| Client          | Angular SPA                       | Operations dashboard, Kubernetes inventory, service logs, persistent task board and API integration |
| Edge            | Spring Cloud Gateway              | Public API entrypoint, routing, future auth/policy enforcement                                      |
| Domain services | Auth, Task, File, Notification    | Bounded-context APIs implemented with Java 21 and Spring Boot 3                                     |
| Worker/services | Go Cache Service, Go Event Worker | Lightweight cache API and async worker foundation                                                   |
| Data            | MongoDB                           | Document persistence owned per service/bounded context                                              |
| Messaging       | RabbitMQ                          | Durable task events, notification queues and async fan-out                                          |
| Cache           | Redis                             | Fast cache/state access and service acceleration                                                    |
| Observability   | Prometheus, Grafana, Actuator     | Metrics scraping, dashboards and operational visibility                                             |
| Delivery        | GitLab CI, Helm, Argo CD          | CI validation, Kubernetes packaging and GitOps deployment flow                                      |

## Visual documentation

| Diagram                                                                          | Description                                                                        |
|----------------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| [Runtime architecture](docs/diagrams/runtime-architecture.svg)                   | End-to-end runtime flow across frontend, gateway, services, data and observability |
| [Messaging and cache flow](docs/diagrams/messaging-cache-flow.svg)               | RabbitMQ and Redis responsibilities with animated message movement                 |
| [Request sequence](docs/diagrams/request-flow-sequence.svg)                      | User request lifecycle from Angular to backend and async notification              |
| [Service port map](docs/diagrams/service-port-map.svg)                           | Local ports, service exposure and operations endpoints                             |
| [Kubernetes Helm view](docs/diagrams/kubernetes-helm-view.svg)                   | Helm-packaged Kubernetes deployment topology                                       |
| [GitLab and Argo CD flow](docs/diagrams/gitlab-argocd-flow.svg)                  | CI/CD and GitOps delivery model                                                    |
| [Frontend operations dashboard](docs/diagrams/frontend-operations-dashboard.svg) | Angular UI flow for Kubernetes inventory, logs and task persistence                |

## Local quick start with WSL2

Use the Linux filesystem for better Docker, Node and Java performance:

```bash
mkdir -p ~/projects
cp -r /mnt/d/workspace/personal/portfolio/nebulaops-v13 ~/projects/nebulaops
cd ~/projects/nebulaops
```

Start the platform:

```bash
chmod +x scripts/*.sh scripts/wsl/*.sh
./scripts/wsl/check-wsl.sh
./scripts/wsl/start.sh
./scripts/wsl/smoke-test.sh
```

Stop and clean local containers:

```bash
./scripts/wsl/stop.sh
# or, for a full reset
./scripts/local-down.sh
```

## Generic Docker quick start

```bash
chmod +x scripts/*.sh
./scripts/verify-local.sh
./scripts/local-up.sh
./scripts/smoke-test.sh
```

## Local service URLs

| Service             | URL                    | Credentials     |
|---------------------|------------------------|-----------------|
| Angular frontend    | http://localhost:4200  | -               |
| API gateway         | http://localhost:8080  | -               |
| Go cache API        | http://localhost:8091  | -               |
| RabbitMQ management | http://localhost:15672 | guest / guest   |
| Prometheus          | http://localhost:9090  | -               |
| Grafana             | http://localhost:3000  | admin / admin   |
| MongoDB             | localhost:27017        | nebula / nebula |
| Redis               | localhost:6379         | -               |

## Core runtime flow

1. The browser loads the Angular operations dashboard.
2. Angular displays Kubernetes namespaces, pods, services and sanitized microservice logs.
3. Angular sends task workflow updates to Spring Cloud Gateway through `/api/tasks`.
4. Gateway routes requests to the task-service.
5. Task-service persists task state in MongoDB.
6. Task-related events are published to RabbitMQ.
7. Notification and worker components consume asynchronous events.
8. Redis supports cache-oriented access patterns.
9. Services expose metrics through Actuator or service endpoints.
10. Prometheus scrapes metrics and Grafana visualizes platform health.

## Documentation index

| Document                                                                         | Purpose                                                                   |
|----------------------------------------------------------------------------------|---------------------------------------------------------------------------|
| [`START_HERE.md`](START_HERE.md)                                                 | First-run guide and project orientation                                   |
| [`docs/TECHNICAL_DOCUMENTATION.md`](docs/TECHNICAL_DOCUMENTATION.md)             | Complete technical architecture and service design                        |
| [`docs/API_EXAMPLES.md`](docs/API_EXAMPLES.md)                                   | API examples and smoke-test calls                                         |
| [`docs/GO_REDIS_RABBITMQ.md`](docs/GO_REDIS_RABBITMQ.md)                         | Go, Redis and RabbitMQ integration design                                 |
| [`docs/GRAFANA_OBSERVABILITY.md`](docs/GRAFANA_OBSERVABILITY.md)                 | Metrics, dashboards and operations guide                                  |
| [`docs/FRONTEND_OPERATIONS_DASHBOARD.md`](docs/FRONTEND_OPERATIONS_DASHBOARD.md) | Angular dashboard for logs, Kubernetes state and persistent task workflow |
| [`docs/HELM_GUIDE.md`](docs/HELM_GUIDE.md)                                       | Helm chart and Kubernetes deployment guide                                |
| [`docs/GITLAB_ARGOCD.md`](docs/GITLAB_ARGOCD.md)                                 | CI/CD and GitOps delivery workflow                                        |
| [`docs/WSL_GUIDE.md`](docs/WSL_GUIDE.md)                                         | Windows 11 + WSL2 development guide                                       |
| [`docs/QUALITY_REPORT.md`](docs/QUALITY_REPORT.md)                               | Validation checklist, limitations and improvement roadmap                 |

## Validation commands

```bash
python3 scripts/validate-package.py
python3 scripts/validate-yaml.py
find scripts -name "*.sh" -print0 | xargs -0 -I{} bash -n {}
./scripts/test-all.sh
```

## Portfolio positioning

This project can be presented as a platform engineering and cloud developer portfolio asset showing:

- microservice decomposition and bounded-context thinking
- event-driven architecture with RabbitMQ
- MongoDB document persistence per service
- Redis-backed caching strategy
- Java and Go polyglot backend implementation
- Angular operations dashboard with Kubernetes inventory, microservice logs and persistent task workflow
- Docker-first local development
- observability with Prometheus and Grafana
- Helm packaging and GitOps delivery design
- professional documentation and runnable scripts

## Technology choices

RabbitMQ is used as the single messaging platform. This keeps the local stack lightweight, reliable and easy to explain
while still demonstrating asynchronous processing, durable queues, retry-friendly architecture and event fan-out
patterns. Kafka and Zookeeper are not part of this distribution.

## Troubleshooting

For Docker Desktop/WSL cache and BuildKit snapshot issues, see [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md).

## Real Kubernetes Console

The Kubernetes console is backed by real gateway APIs, not frontend mock data. The gateway container includes `kubectl`,
reads kubeconfig from `/kube/config`, and applies operations directly against the configured cluster.

Supported operations include namespace/workload/service/ingress/config map/secret listing, YAML retrieval, YAML apply,
resource deletion, workload scaling and pod/service logs. See `docs/REAL_KUBERNETES_CONSOLE.md` for setup and safety
notes.
