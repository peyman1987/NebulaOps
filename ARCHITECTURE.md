# NebulaOps v21.3 — Architecture

## Component overview

```
                     ┌──────────────────────────────────────┐
                     │            Browser (4200)            │
                     └──────────────────┬───────────────────┘
                                        │ HTTPS
                                        ▼
                     ┌──────────────────────────────────────┐
                     │       Frontend (nginx + Angular 18)  │
                     │  Loads /api/* → proxy_pass gateway   │
                     └──────────────────┬───────────────────┘
                                        │ HTTP
                                        ▼
        ┌───────────────────────────────────────────────────────────────────┐
        │            Gateway Service (Spring Boot 3.3, MVC)  :8080         │
        │  ┌─────────────────────┐  ┌───────────────────────────────────┐  │
        │  │ Live endpoints      │  │ Proxy controller (downstream)     │  │
        │  │ /api/kubernetes     │  │ /api/auth/*    → auth-service     │  │
        │  │ /api/runtime        │  │ /api/tasks     → task-service     │  │
        │  │ /api/platform       │  │ /api/ai-ops/*  → ai-ops-service   │  │
        │  │ /api/health         │  │ /api/pipeline  → pipeline-service │  │
        │  └─────────────────────┘  │ /api/cost      → cost-service     │  │
        │                           │ /api/audit     → observability    │  │
        │                           │ /api/secrets   → devsecops        │  │
        │                           │ /api/registry  → devsecops        │  │
        │                           │ /api/notifications → notif-svc    │  │
        │                           └───────────────────────────────────┘  │
        └────┬──────────────────────────────────┬───────────────────────────┘
             │ shell out via                    │ HTTP
             │ /opt/nebula-tools/{kubectl,...}  │
             ▼                                  ▼
   ┌─────────────────────┐         ┌──────────────────────────────────┐
   │ Host runtime tools  │         │  11 microservices                │
   │ kubectl, docker,    │         │  auth, task, file,               │
   │ helm, terraform,    │         │  notification, ai-ops,           │
   │ trivy, argocd       │         │  devsecops, pipeline,            │
   └─────────────────────┘         │  observability, gitops,          │
                                   │  env-manager, terraform,         │
                                   │  cost-analytics (v21.3 NEW)      │
                                   └──────────┬───────────────────────┘
                                              │
                  ┌───────────────────────────┼──────────────────────────┐
                  ▼                           ▼                          ▼
            ┌──────────┐               ┌──────────┐              ┌──────────┐
            │ MongoDB  │               │ RabbitMQ │              │  Redis   │
            └──────────┘               └──────────┘              └──────────┘

         + Observability sidecar stack:
           Prometheus :9090 | Loki :3100 | Tempo :3200 | Grafana :3000
           OpenTelemetry Collector :4318
```

## Service catalog

| Service                       | Tech            | Port | Purpose                                      |
| ----------------------------- | --------------- | ---- | -------------------------------------------- |
| gateway-service               | Spring MVC      | 8080 | API gateway, proxy, live infra endpoints     |
| auth-service                  | Spring Boot     | 8081 | User registration, dev-mode login            |
| task-service                  | Spring Boot     | 8082 | Task CRUD, publishes RabbitMQ events         |
| notification-service          | Spring Boot     | 8083 | Consumes events, persists notifications      |
| file-service                  | Spring Boot     | 8084 | File metadata + storage abstraction          |
| ai-ops-service                | Spring Boot     | 8085 | Wraps Python AI engine, runs diagnostics     |
| devsecops-service             | Spring Boot     | 8086 | Trivy scans, secret scanning, registry       |
| pipeline-engine-service       | Spring Boot     | 8087 | CI/CD pipeline state + run history           |
| observability-service         | Spring Boot     | 8092 | Trace/metric/log aggregator + audit events   |
| gitops-control-service        | Spring Boot     | 8093 | ArgoCD wrapper                               |
| environment-manager-service   | Spring Boot     | 8094 | Namespace/env lifecycle                      |
| terraform-studio-service      | Spring Boot     | 8096 | Terraform plan/apply orchestrator            |
| **cost-analytics-service**    | **Spring Boot** | **8097** | **v21.3 NEW — FinOps cost aggregation**  |
| ai-engine                     | Python FastAPI  | 8095 | Anomaly detection, log clustering            |
| go-cache-service              | Go              | 8091 | High-performance cache layer                 |
| go-event-worker               | Go              | —    | Background queue consumer                    |

## v21.3 Changes

### New: cost-analytics-service (:8097)
REST API for the FinOps tab. Aggregates cost data from MongoDB `cost_entries`
collection. Falls back to static defaults when offline.
Routes proxied through gateway: `GET /api/cost/summary`, `GET /api/cost/breakdown`, `POST /api/cost/entries`.

### Enhanced: gateway ProxyController
Added proxy routes for:
- `GET/PATCH /api/notifications/**` → notification-service
- `GET /api/pipeline/runs` → pipeline-engine-service
- `GET /api/audit/events` → observability-service
- `GET /api/secrets/list` → devsecops-service
- `GET /api/registry/images` → devsecops-service
- `GET /api/cost/summary` → cost-analytics-service

### Frontend improvements
- New CSS design tokens: `--bg-glass`, `--bg-card`, `--border-faint`, `--border-glow`
- `--accent-teal`, `--accent-indigo` added
- `--grad-royal`, `--grad-ocean` gradients
- Richer shadow set: `--shadow-xs`, `--shadow-card`
- Improved `.skeleton`, `.toast`, `.status-pill`, `.empty-state` utilities
- `--font-mono` updated to prefer JetBrains Mono
- Extended spacing: `--space-10`, `--space-12`
- Motion: `--motion-snap`, `--motion-spring` added
- Z-index scale: `--z-base`, `--z-raised`, `--z-overlay`, `--z-modal`, `--z-toast`
- `:focus-visible` now applies `border-radius` for consistency
- Print media query added
- Scrollbar uses `background-clip: content-box` for cleaner rendering

## Data flow examples

### Frontend loads cost summary (v21.3 new)

```
Angular  ──GET /api/cost/summary──▶  nginx
                                       │
                                       ▼
                                     gateway-service (ProxyController)
                                       │
                                       │ rest.getForObject
                                       ▼
                                     cost-analytics-service:8097
                                       │
                                       │ MongoDB aggregation / static seed
                                       ▼
                             { monthly, delta, currency, breakdown, live }
```

## Configuration sources

| Setting               | Source                                                    |
| --------------------- | --------------------------------------------------------- |
| Service URLs          | `config/platform.yml` (canonical)                        |
| Gateway proxy targets | `backend/gateway-service/.../application.yml`            |
| Frontend API base     | `frontend/src/app/api.config.ts`                         |
| Stack composition     | `docker-compose.yml`                                     |
| Environment overrides | Container env vars (set in compose file)                 |

## Failure semantics

- **Live endpoints** (`kubectl`/`docker`/`helm`-backed): return JSON with
  `live: false` and a `toolStatus` field when the tool is unavailable,
  never throw 500.
- **Proxy endpoints**: catch `ResourceAccessException` and return empty
  fallback (`[]` for lists, `{error: ...}` for actions). The frontend
  treats absent data gracefully with empty-state UI.
- **Gateway crash**: nginx returns 502; `restart-gateway.sh` brings it
  back without restarting the whole stack.

## Version

This document describes **v21.3.0** topology. See `RELEASE_NOTES_v21.3.md`.
