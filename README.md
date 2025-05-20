# NebulaOps v21.2

End-to-end DevOps and platform engineering portfolio: Angular frontend, Spring Boot
microservices, Go services, Python AI engine, full observability stack
(Prometheus, Loki, Tempo, Grafana), GitOps and DevSecOps tooling — all
orchestrated locally with Docker Compose.

## Quickstart (WSL2 or Linux)

```bash
./scripts/wsl/start.sh
```

Visit **http://localhost:4200**.

To force-rebuild the gateway after editing controllers or proxy config:
```bash
./scripts/wsl/start.sh --rebuild-gateway
```

## Architecture overview

```
┌──────────┐     ┌──────────────┐     ┌────────────────────────┐
│ Browser  │ →   │  Frontend    │ →   │  Gateway (Spring MVC)  │
│ :4200    │     │  (nginx)     │     │  :8080                 │
└──────────┘     └──────────────┘     └────────────────────────┘
                                                │
                ┌───────────────┬───────────────┼───────────────┬───────────────┐
                ▼               ▼               ▼               ▼               ▼
            auth-svc        task-svc       ai-ops-svc     pipeline-svc     ... 9 more
            (Spring         (Spring        (Spring        (Spring          backend
             Boot 21)       Boot 21)       Boot 21)       Boot 21)         services
                │                              │
                ▼                              ▼
            MongoDB                       Python ai-engine
            RabbitMQ                      (FastAPI :8095)
            Redis
```

The **gateway** owns:
- Live infrastructure endpoints (Kubernetes/Docker/Helm/Terraform/Grafana,
  via `kubectl`/`docker`/`helm`/`terraform` on the host)
- Frontend-shaped responses (`/api/kubernetes/snapshot`, `/api/platform/*`,
  `/api/runtime/*`) so Angular doesn't have to reshape JSON
- HTTP proxying to downstream microservices (`/api/tasks`, `/api/auth/*`,
  `/api/ai-ops/*`)

## Centralized configuration

All service URLs come from one file:

**`config/platform.yml`** — service hostnames, ports, API routes

Consumers:
- `frontend/src/app/api.config.ts` — typed API wrapper used by `app.component.ts`
- `backend/gateway-service/src/main/resources/application.yml` — `proxy.*` keys
- `docker-compose.yml` — environment variables injected per service

Change a port or hostname → edit `config/platform.yml`, propagate to the three
consumers above, restart.

## Common operations

| Task                        | Command                                     |
| --------------------------- | ------------------------------------------- |
| Start everything            | `./scripts/wsl/start.sh`                    |
| Force gateway rebuild       | `./scripts/wsl/start.sh --rebuild-gateway`  |
| Quick gateway restart       | `./scripts/wsl/restart-gateway.sh`          |
| Stop everything             | `./scripts/wsl/stop.sh`                     |
| Health check all services   | `./scripts/wsl/health.sh`                   |
| Tail a service              | `./scripts/wsl/logs.sh gateway-service`     |
| Gateway logs + health probe | `./scripts/wsl/gateway-logs.sh`             |
| Diagnose runtime tools      | `./scripts/wsl/diagnose-runtime.sh`         |
| Reset Docker cache (last resort) | `./scripts/wsl/docker-cache-repair.sh` |

## Service URLs

| Service        | URL                              | Credentials   |
| -------------- | -------------------------------- | ------------- |
| Frontend       | http://localhost:4200            | —             |
| Gateway        | http://localhost:8080            | —             |
| Grafana        | http://localhost:3000            | admin/admin   |
| Prometheus     | http://localhost:9090            | —             |
| Loki           | http://localhost:3100            | —             |
| Tempo          | http://localhost:3200            | —             |
| Mongo Express  | http://localhost:8088            | admin/admin   |
| Redis Commander| http://localhost:8089            | admin/admin   |
| RabbitMQ       | http://localhost:15672           | guest/guest   |
| AI Engine      | http://localhost:8095/docs       | —             |

## Required host tools

Mounted into containers from `.runtime-tools/`:
- `kubectl` (auto-detected from host)
- `docker` (uses host daemon socket)
- `helm`

Optional (endpoints return `live:false` if missing):
- `trivy`, `terraform`, `argocd`

## Troubleshooting

**Gateway returns 502 on all endpoints**
The image is stale. Run:
```bash
./scripts/wsl/start.sh --rebuild-gateway
./scripts/wsl/gateway-logs.sh
```

**`kubectl` reports no cluster**
Install kind or docker-desktop's kubernetes, or update `.kube/config`. The
gateway will keep working — affected endpoints return `live: false`.

**Frontend shows "Disconnected"**
Run `./scripts/wsl/health.sh` to see which downstream service is down.

## Project layout

```
.
├── config/platform.yml              ← centralized URL registry
├── docker-compose.yml               ← stack definition
├── frontend/                        ← Angular 18
│   └── src/app/api.config.ts        ← typed API wrapper
├── backend/                         ← Spring Boot 3.3 / Java 21
│   ├── gateway-service/             ← MVC gateway + proxy
│   ├── auth-service/
│   ├── task-service/
│   ├── ai-ops-service/
│   └── ... 8 more services
├── go/                              ← Go cache + event worker
├── ai-engine/                       ← Python FastAPI inference
├── infrastructure/                  ← Helm charts, Grafana, OTel configs
├── terraform/                       ← IaC modules
├── docs/                            ← Diagrams + ADRs
└── scripts/wsl/                     ← Operational scripts (use lib/common.sh)
```

## Version

**v21.2.1** — see `RELEASE_NOTES_v21.2.md` for details.
