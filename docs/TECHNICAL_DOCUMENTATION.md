# NebulaOps Technical Documentation

## 1. Executive summary

NebulaOps is a senior-level cloud-native portfolio project implemented as an event-driven SaaS workflow platform. The
current version is aligned to **Angular + Spring Boot + Go + MongoDB + Kafka + Redis + RabbitMQ + Helm + Prometheus +
Grafana + GitLab CI/CD + Argo CD**.

The goal is to show application engineering and platform engineering together: API design, service boundaries,
asynchronous messaging, document modeling, caching, operational queues, containerization, Kubernetes packaging,
observability and GitOps delivery.

## 2. Current technology stack

| Layer                | Technology                                     |
|----------------------|------------------------------------------------|
| Frontend             | Angular, TypeScript, Angular CDK drag and drop |
| API Gateway          | Spring Cloud Gateway                           |
| Java backend         | Java 21, Spring Boot 3 microservices           |
| Go backend           | Go cache API and RabbitMQ event worker         |
| Database             | MongoDB per bounded context                    |
| Event streaming      | Apache Kafka                                   |
| Operational queue    | RabbitMQ                                       |
| Cache                | Redis                                          |
| Local runtime        | Docker Compose                                 |
| Kubernetes packaging | Helm                                           |
| GitOps deployment    | Argo CD Application and ApplicationSet         |
| CI/CD                | GitLab CI/CD                                   |
| Metrics              | Spring Boot Actuator, Micrometer, Prometheus   |
| Dashboards           | Grafana provisioning as code                   |

## 3. Diagrams

### Runtime architecture

![Runtime architecture](diagrams/runtime-architecture.svg)

### GitLab CI/CD and Argo CD

![GitLab ArgoCD flow](diagrams/gitlab-argocd-flow.svg)

### Messaging and cache

![Messaging cache flow](diagrams/messaging-cache-flow.svg)

### Kubernetes and Helm

![Kubernetes Helm view](diagrams/kubernetes-helm-view.svg)

### Request flow sequence

![Request flow sequence](diagrams/request-flow-sequence.svg)

### Service and port map

![Service port map](diagrams/service-port-map.svg)

## 4. Runtime architecture

```text
Angular Frontend
     |
     v
Spring Cloud Gateway
     |
     +--> Auth Service ------------ MongoDB: nebula_auth
     +--> Task Service ------------ MongoDB: nebula_task
     |          |
     |          +------------------> Kafka: nebula.task.events
     |
     +--> File Service ------------ MongoDB: nebula_file
     +--> Notification Service <---- Kafka consumer group
     |          |
     |          +------------------> MongoDB: nebula_notification
     |
     +--> Go Cache Service -------- Redis

Task or Notification jobs --------> RabbitMQ --------> Go Event Worker

Observability:
Services /actuator/prometheus + Go /metrics --> Prometheus --> Grafana

Delivery:
GitLab CI builds/tests/packages --> Registry/Git --> Argo CD syncs Helm chart --> Kubernetes
```

## 5. Bounded contexts and services

### Angular frontend

Enterprise-style UI foundation with dashboard, task board and drag-and-drop interactions. It talks to the backend
through the gateway instead of calling services directly.

### Gateway service

Routes public API traffic to internal services.

```text
/api/auth/**          -> auth-service
/api/tasks/**         -> task-service
/api/files/**         -> file-service
/api/notifications/** -> notification-service
/cache/**             -> go-cache-service
```

### Auth service

Responsible for identity and organization context.

Current API:

```http
POST /api/auth/register
POST /api/auth/login
GET  /api/auth/healthz
```

Senior hardening roadmap:

- BCrypt password hashing
- JWT signing key from secret manager
- refresh token rotation
- RBAC claim generation
- tenant claim validation

### Task service

Responsible for workflow task documents and Kafka task events.

Current API:

```http
GET   /api/tasks?organizationId=demo-org
POST  /api/tasks
PATCH /api/tasks/{id}/status/{status}
```

Events produced:

```text
TaskCreated
TaskStatusChanged
```

Kafka topic:

```text
nebula.task.events
```

### Notification service

Consumes Kafka task events and exposes generated notifications.

Current API:

```http
GET /api/notifications
```

### File service

Stores file metadata and is ready for S3/MinIO integration.

Current API:

```http
POST /api/files
GET  /api/files
```

### Go cache service

Small Go backend that demonstrates non-Java backend capability and Redis integration.

Responsibilities:

- cache values
- read cached values
- expose lightweight health endpoint
- prepare rate-limiting/session-cache extension points

### Go event worker

RabbitMQ worker that consumes operational queue messages. It is separated from Kafka because queue jobs and event
streams solve different problems.

## 6. Data and messaging design

### MongoDB

Each service owns its own database to avoid shared-schema coupling.

```text
nebula_auth
nebula_task
nebula_file
nebula_notification
```

Important collections:

```text
users
tasks
files
notifications
```

The task collection should be indexed by `organizationId` for tenant-scoped queries.

### Kafka

Kafka is the durable, replayable event backbone. It is used for domain events and future analytics/audit pipelines.

Event example:

```json
{
  "eventId": "uuid",
  "type": "TaskCreated",
  "taskId": "mongo-id",
  "organizationId": "demo-org",
  "occurredAt": "2026-05-17T10:15:30Z"
}
```

### RabbitMQ

RabbitMQ is used for operational work queues where a worker takes a job, processes it and acknowledges completion. Good
use cases: email, PDF generation, retries, webhooks and background jobs.

### Redis

Redis is used for fast runtime state:

- cache
- rate limiting
- session-like temporary state
- hot dashboard counters

## 7. Local development

Start the full platform:

```bash
chmod +x scripts/*.sh
./scripts/verify-local.sh
./scripts/local-up.sh
```

Open:

```text
Frontend:      http://localhost:4200
Gateway:       http://localhost:8080
Go Cache API:  http://localhost:8091
Grafana:       http://localhost:3000
Prometheus:    http://localhost:9090
RabbitMQ UI:   http://localhost:15672
```

Run smoke tests:

```bash
./scripts/smoke-test.sh
```

## 8. WSL development

For Windows 11 + WSL2:

```bash
chmod +x scripts/*.sh scripts/wsl/*.sh
./scripts/wsl/check-wsl.sh
./scripts/wsl/start.sh
./scripts/wsl/smoke-test.sh
```

Keep the repo under the Linux filesystem, for example:

```text
~/projects/nebulaops
```

## 9. Helm deployment

Helm chart location:

```text
infrastructure/helm/nebulaops
```

Render manifests:

```bash
./scripts/helm-render.sh
```

Install locally:

```bash
./scripts/helm-install-local.sh
```

## 10. Observability

Spring services expose:

```text
/actuator/health
/actuator/metrics
/actuator/prometheus
```

Prometheus config:

```text
infrastructure/observability/prometheus/prometheus.yml
```

Grafana provisioning:

```text
infrastructure/observability/grafana/provisioning
infrastructure/observability/grafana/dashboards/nebulaops-overview.json
```

## 11. GitLab CI/CD and Argo CD GitOps

Pipeline stages:

```text
validate -> test -> build -> package -> deploy -> verify
```

Responsibilities:

- GitLab validates docs, YAML, scripts and package structure
- GitLab runs service tests where tooling is available
- GitLab builds Docker images
- GitLab packages Helm chart metadata
- Argo CD reconciles Kubernetes state from the Git repository

Files:

```text
.gitlab-ci.yml
infrastructure/argocd/project.yaml
infrastructure/argocd/application.yaml
infrastructure/argocd/applicationset.yaml
```

## 12. Production hardening checklist

- Replace demo auth with signed JWT and BCrypt
- Move MongoDB, Kafka, RabbitMQ and Redis to managed services where appropriate
- Add API rate limiting at Gateway using Redis
- Add OpenTelemetry tracing
- Add centralized structured logs
- Use External Secrets Operator
- Use NetworkPolicies
- Add resource requests and limits
- Add Horizontal Pod Autoscaling
- Add integration tests with Testcontainers
- Add contract tests for Kafka events
- Add DLQ strategy for RabbitMQ jobs

## 13. Interview story

> I designed NebulaOps as a portfolio-grade cloud-native SaaS platform. Angular provides the user-facing application,
> Spring Boot services model core bounded contexts, Go services demonstrate polyglot backend capability, MongoDB stores
> service-owned documents, Kafka handles durable domain event streams, RabbitMQ handles operational queues, Redis
> supports
> cache and runtime state, and Helm/Argo CD provide production-style GitOps delivery. Prometheus and Grafana are
> provisioned as code so the platform can be operated, not only developed.

## v12 quality hardening notes

This optimized package includes the following improvements:

- Prometheus scrape configuration corrected so `go-cache-service` is a first-class scrape job instead of being nested
  under `file-service`.
- Angular workspace metadata cleaned to remove the obsolete `defaultProject` warning on Angular 18 builds.
- Additional SVG diagrams added for request sequencing and local service/port mapping.
- Static validation extended to parse all SVG files, check Prometheus job names, validate shell syntax and verify
  important documentation references.
- Go sources formatted with `gofmt`; Go module tests are executed per module through `scripts/test-all.sh`.

### Validation commands used

```bash
python3 scripts/validate-package.py
python3 scripts/validate-yaml.py
bash scripts/test-all.sh
npm run build --prefix frontend
```

Note: Maven was not available in the current sandbox, so Java compilation is covered by static package validation here
and remains executable in CI/GitLab where Maven is installed.

## Author

**Peyman Eshghi Malayeri**  
Email: peyman_em@yahoo.com  
Project Year: 2024
