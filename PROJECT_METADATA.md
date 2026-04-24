# NebulaOps — Project Metadata

| Key             | Value                                       |
| --------------- | ------------------------------------------- |
| name            | NebulaOps                                   |
| version         | v23.2.0                                     |
| release_date    | 2026-05                                     |
| repo_layout     | monorepo (frontend + backend + infra)       |
| maintainer      | Peyman Eshghi Malayeri                      |
| license         | MIT                                         |

## Tech stack

- **Frontend**: Angular 18, TypeScript, Angular CDK
- **Backend**: Spring Boot 3.3, Java 21 (14 microservices)
- **Auxiliary services**: Go 1.23, Python 3.12 (FastAPI)
- **Data**: MongoDB 7, Redis 7, RabbitMQ 3.13
- **Observability**: Prometheus, Loki, Tempo, Grafana, OpenTelemetry
- **Infra-as-Code**: Terraform, Helm, ArgoCD
- **Security**: Keycloak, Trivy
- **Container orchestration**: Docker Compose (local), Kubernetes (production)

## Service count

| Category           | Count | Services                                                                |
| ------------------ | ----- | ----------------------------------------------------------------------- |
| Spring Boot BE     | 14    | gateway, auth, task, notification, file, ai-ops, devsecops, pipeline, observability, gitops, environment, terraform, **cost**, spring-mvc |
| Python AI          | 1     | ai-engine (FastAPI)                                                     |
| Go                 | 2     | cache-service, event-worker                                             |
| Infrastructure     | 5     | MongoDB, Redis, RabbitMQ, Keycloak, GitLab                              |
| Observability      | 4     | Prometheus, Loki, Tempo, Grafana                                        |

## Single source of truth files

| File                                          | Purpose                              |
| --------------------------------------------- | ------------------------------------ |
| `config/platform.yml`                         | Service URLs, ports, API routes      |
| `frontend/src/app/api.config.ts`              | Typed API wrapper                    |
| `frontend/src/styles.css`                     | Global design tokens                 |
| `backend/gateway-service/.../application.yml` | Proxy targets                        |
| `docker-compose.yml`                          | Stack composition                    |

## API surface (v23.2)

| Route group             | Endpoints                                      |
| ----------------------- | ---------------------------------------------- |
| `/api/auth/**`          | login, register                                |
| `/api/tasks/**`         | CRUD, status patch                             |
| `/api/kubernetes/**`    | snapshot, logs                                 |
| `/api/runtime/**`       | docker containers/images/volumes, helm         |
| `/api/platform/**`      | observability, gitops, devsecops, environments |
| `/api/ai-ops/**`        | analyze, autofix                               |
| `/api/pipeline/**`      | runs list, trigger (v23.2)                     |
| `/api/cost/**`          | summary, breakdown, entries (v23.2)            |
| `/api/notifications/**` | live, mark-read (v23.2)                        |
| `/api/audit/**`         | events (v23.2)                                 |
| `/api/secrets/**`       | list (v23.2)                                   |
| `/api/registry/**`      | images (v23.2)                                 |

## Entry points

- `./scripts/wsl/start.sh` — start everything
- `./scripts/wsl/health.sh` — check all services
- `./scripts/wsl/restart-gateway.sh` — fast gateway restart


## v23.2 Extension Set

- APIForge — `extensions/apiforge` — NodePort `31110`

## v23.2 Extension Selection

Installed UI-controlled extensions: APIForge, KubeBridge and Contract Hub.

Control actions exposed through the NebulaOps gateway and APP BAR: Start, Stop, Restart, Status and Open.
