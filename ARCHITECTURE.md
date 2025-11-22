# NebulaOps v22.2 — Architecture

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
   │ Host runtime tools  │         │  14 microservices                │
   │ kubectl, docker,    │         │  auth, task, file,               │
   │ helm, terraform,    │         │  notification, ai-ops,           │
   │ trivy, argocd       │         │  devsecops, pipeline,            │
   └─────────────────────┘         │  observability, gitops,          │
                                   │  env-manager, terraform,         │
                                   │  cost-analytics, spring-mvc      │
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

         + Identity and source control:
           Keycloak :8180 | GitLab :8929
```

## Service catalog

| Service                       | Tech            | Port | Purpose                                      |
| ----------------------------- | --------------- | ---- | -------------------------------------------- |
| gateway-service               | Spring Boot MVC | 8080 | API gateway, proxy, live infra endpoints     |
| auth-service                  | Spring Boot     | 8081 | User registration, legacy bootstrap endpoints |
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
| **cost-analytics-service**    | **Spring Boot** | **8097** | **FinOps cost aggregation**                  |
| spring-mvc-service             | Spring Boot MVC | 8099 | Demo Spring MVC application behind Keycloak  |
| keycloak                       | Identity        | 8180 | OIDC provider for frontend, GitLab and APIs  |
| gitlab                         | SCM / CI        | 8929 | Source control service using Keycloak OIDC   |
| ai-engine                     | Python FastAPI  | 8095 | Anomaly detection, log clustering            |
| go-cache-service              | Go              | 8091 | High-performance cache layer                 |
| go-event-worker               | Go              | —    | Background queue consumer                    |

## v22.2 Changes

### Platform identity and GitLab runtime
Keycloak now acts as the shared OIDC provider for the Angular frontend, GitLab and every Spring Boot service. GitLab CE is part of the local stack and uses the `gitlab` Keycloak client for OpenID Connect login.

### cost-analytics-service (:8097)
REST API for the FinOps tab. Aggregates cost data from MongoDB `cost_entries` collection. Falls back to static defaults when offline. Routes proxied through gateway: `GET /api/cost/summary`, `GET /api/cost/breakdown`, `POST /api/cost/entries`.

### Enhanced: gateway ProxyController and token relay
The gateway validates Keycloak JWTs when `KEYCLOAK_AUTH_ENABLED=true`, relays Bearer tokens downstream through RestTemplate, and includes proxy routes for:
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

### Frontend loads cost summary (v22.2 new)

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

This document describes **v22.2.0** topology. See `RELEASE_NOTES_v22.2.md`.

## Shared library layer introduced in v22.2 patch

The platform now includes an explicit shared-library layer to avoid duplicating bootstrap, session and contract code across deployable units.

### Frontend MFE shared core

`frontend/libs/nebulaops-mfe-core` provides the common Angular Element bootstrap, JWT interceptor, Keycloak storage keys and gateway URL helper used by all remote micro frontends. Each remote keeps its feature UI and API calls locally, while the infrastructure code for registering the custom element and propagating the shell token is centralized.

### Backend shared kernel

`backend/nebulaops-shared-kernel` is registered in the Maven reactor and contains stable cross-service contracts such as API envelopes, error envelopes, security constants, service identity records and REST path helpers. New micro backend work should add reusable DTOs/constants there instead of redefining them in each service.


## INFRA Hub MFE

NebulaOps v22.2 includes a dedicated `infra-hub` micro frontend on port `4220`. It restores the previous INFRA console experience as an independently deployable remote and links observability, data, runtime, gateway and GitOps endpoints.
