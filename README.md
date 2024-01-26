> Enterprise Cloud-Native Portfolio Project  
> Developed by Peyman Eshghi Malayeri (2024)  
> Contact: peyman_em@yahoo.com

# NebulaOps Platform

## v11 Docs + SVG Aligned Edition

NebulaOps is a senior cloud-native portfolio project that demonstrates a realistic distributed SaaS platform using *
*Angular**, **Spring Boot**, **Go**, **MongoDB**, **Kafka**, **Redis**, **RabbitMQ**, **Docker Compose**, **Helm**, *
*Prometheus**, **Grafana**, **GitLab CI/CD** and **Argo CD GitOps**.

## Visual architecture

### Runtime architecture

![Runtime architecture](docs/diagrams/runtime-architecture.svg)

### GitLab CI/CD and Argo CD flow

![GitLab ArgoCD flow](docs/diagrams/gitlab-argocd-flow.svg)

### Messaging and cache responsibilities

![Messaging cache flow](docs/diagrams/messaging-cache-flow.svg)

### Kubernetes and Helm deployment

![Kubernetes Helm view](docs/diagrams/kubernetes-helm-view.svg)

### Request sequence

![Request flow sequence](docs/diagrams/request-flow-sequence.svg)

### Local service and port map

![Service port map](docs/diagrams/service-port-map.svg)

## What runs locally

| Area               | Component                           |
|--------------------|-------------------------------------|
| Frontend           | Angular SPA on `4200`               |
| Gateway            | Spring Cloud Gateway on `8080`      |
| Java services      | Auth, Task, File, Notification      |
| Go services        | Cache API and RabbitMQ event worker |
| Data               | MongoDB                             |
| Event streaming    | Kafka                               |
| Operational queues | RabbitMQ                            |
| Cache              | Redis                               |
| Observability      | Prometheus and Grafana              |
| Delivery           | GitLab CI/CD and Argo CD manifests  |

## WSL Ubuntu quick start

Use this on Windows 11 with Ubuntu WSL2 and Docker Desktop.

```bash
chmod +x scripts/*.sh scripts/wsl/*.sh
./scripts/wsl/check-wsl.sh
./scripts/wsl/start.sh
./scripts/wsl/smoke-test.sh
```

Recommended path:

```text
~/projects/nebulaops
```

Avoid running the repository from `/mnt/c/...` because Docker bind mounts and file watching are slower there.

## Local start without WSL wrappers

```bash
chmod +x scripts/*.sh
./scripts/verify-local.sh
./scripts/local-up.sh
./scripts/smoke-test.sh
```

Open:

```text
Frontend:      http://localhost:4200
Gateway:       http://localhost:8080
Go Cache API:  http://localhost:8091
Grafana:       http://localhost:3000  admin/admin
Prometheus:    http://localhost:9090
RabbitMQ UI:   http://localhost:15672  guest/guest
```

## Why Kafka, RabbitMQ and Redis all exist

Kafka is the durable event backbone for domain events such as `TaskCreated` and `TaskStatusChanged`. RabbitMQ is the
operational work queue for jobs that should be consumed by workers, retried and completed. Redis is used for fast cache,
rate limiting and temporary runtime state.

## GitLab CI/CD and Argo CD

GitLab CI validates the repository, runs tests, builds service images, packages Helm artifacts and prepares deployment
metadata. Argo CD owns continuous delivery by reconciling the Helm chart into Kubernetes.

Main files:

```text
.gitlab-ci.yml
infrastructure/argocd/project.yaml
infrastructure/argocd/application.yaml
infrastructure/argocd/applicationset.yaml
infrastructure/helm/nebulaops
```

Validate GitLab/Argo CD files:

```bash
./scripts/gitlab-validate.sh
```

## Helm and Kubernetes

Render the Helm chart:

```bash
./scripts/helm-render.sh
```

Install to a local Kubernetes cluster:

```bash
./scripts/helm-install-local.sh
```

## Documentation map

| Document                          | Purpose                                        |
|-----------------------------------|------------------------------------------------|
| `docs/TECHNICAL_DOCUMENTATION.md` | Complete technical overview aligned to v11     |
| `docs/GITLAB_ARGOCD.md`           | GitLab CI/CD and Argo CD workflow              |
| `docs/GO_REDIS_RABBITMQ.md`       | Go services, Redis and RabbitMQ explanation    |
| `docs/HELM_GUIDE.md`              | Helm packaging and local Kubernetes deployment |
| `docs/GRAFANA_OBSERVABILITY.md`   | Prometheus/Grafana setup                       |
| `docs/WSL_GUIDE.md`               | Windows 11 + WSL instructions                  |
| `docs/API_EXAMPLES.md`            | API examples and smoke-test calls              |
| `docs/QUALITY_REPORT.md`          | Validation report                              |

## Validation

```bash
./scripts/test-all.sh
python3 scripts/validate-package.py
python3 scripts/validate-yaml.py
find scripts -name "*.sh" -print0 | xargs -0 -I{} bash -n {}
```

The package includes static validation for docs, SVG XML, YAML, JSON, script syntax, Prometheus scrape jobs and expected
stack references. Frontend and Go tests are run automatically when Node.js and Go are installed.
