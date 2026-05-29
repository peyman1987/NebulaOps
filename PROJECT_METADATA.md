# NebulaOps — Project Metadata

| Key             | Value                                       |
| --------------- | ------------------------------------------- |
| name            | NebulaOps                                   |
| version         | v24.1.0                                     |
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

## API surface (v24.1)

| Route group             | Endpoints                                      |
| ----------------------- | ---------------------------------------------- |
| `/api/auth/**`          | login, register                                |
| `/api/tasks/**`         | CRUD, status patch                             |
| `/api/kubernetes/**`    | snapshot, logs                                 |
| `/api/runtime/**`       | docker containers/images/volumes, helm         |
| `/api/platform/**`      | observability, gitops, devsecops, environments |
| `/api/ai-ops/**`        | analyze, autofix                               |
| `/api/pipeline/**`      | runs list, trigger (v24.1)                     |
| `/api/cost/**`          | summary, breakdown, entries (v24.1)            |
| `/api/notifications/**` | live, mark-read (v24.1)                        |
| `/api/audit/**`         | events (v24.1)                                 |
| `/api/secrets/**`       | list (v24.1)                                   |
| `/api/registry/**`      | images (v24.1)                                 |

## Entry points

- `./scripts/wsl/start.sh` — start everything
- `./scripts/wsl/health.sh` — check all services
- `./scripts/wsl/restart-gateway.sh` — fast gateway restart


## v24.1 Extension Set

- APIForge — `extensions/apiforge` — NodePort `31110`

## v24.1 Extension Selection

Installed UI-controlled extensions: APIForge, KubeBridge and Contract Hub.

Control actions exposed through the NebulaOps gateway and the separate EXTENSIONS control panel: Start, Stop, Restart, Status and Open.

## v24.1 release alignment guard

- `scripts/validate-version-alignment-v24.1.py` — verifies docs, package metadata, Maven versions, Docker identifiers, WSL script names and frontend runtime assets.
- `scripts/wsl/smoke-version-alignment-v24.1.sh` — WSL-friendly smoke wrapper for release identity checks.

## v24.1 build and frontend architecture controls

- `./scripts/wsl/start.sh --core` starts the fast local core runtime.
- `./scripts/wsl/start.sh --full` starts the complete profiled runtime.
- `./scripts/wsl/build-frontend-changed.sh` runs the selective frontend build cache.
- `frontend/libs/nebulaops-api-client` centralizes API calls and bearer token handling.
- `frontend/libs/nebulaops-live-state` standardizes source states.
- `frontend/libs/nebulaops-mfe-runtime` standardizes MFE bootstrap and live-only payload checks.
- `frontend/libs/nebulaops-ui-kit` provides UI Kit v2 density, action bar and side panel contracts.
