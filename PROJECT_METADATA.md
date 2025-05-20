# NebulaOps — Project Metadata

| Key             | Value                                       |
| --------------- | ------------------------------------------- |
| name            | NebulaOps                                   |
| version         | v21.2.1                                     |
| release_date    | 2026-05                                     |
| repo_layout     | monorepo (frontend + backend + infra)       |
| maintainer      | Peyman Eshghi Malayeri                      |
| license         | MIT                                         |

## Tech stack

- **Frontend**: Angular 18, TypeScript, Angular CDK
- **Backend**: Spring Boot 3.3, Java 21
- **Auxiliary services**: Go 1.23, Python 3.12 (FastAPI)
- **Data**: MongoDB 7, Redis 7, RabbitMQ 3.13
- **Observability**: Prometheus, Loki, Tempo, Grafana, OpenTelemetry
- **Infra-as-Code**: Terraform, Helm, ArgoCD
- **Security**: Trivy
- **Container orchestration**: Docker Compose (local), Kubernetes (production)

## Single source of truth files

| File                                | Purpose                              |
| ----------------------------------- | ------------------------------------ |
| `config/platform.yml`               | Service URLs, ports, API routes      |
| `frontend/src/app/api.config.ts`    | Typed API wrapper                    |
| `backend/gateway-service/.../application.yml` | Proxy targets               |
| `docker-compose.yml`                | Stack composition                    |

## Entry points

- `./scripts/wsl/start.sh` — start everything
- `./scripts/wsl/health.sh` — check all services
- `./scripts/wsl/restart-gateway.sh` — fast gateway restart
