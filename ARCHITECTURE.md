# NebulaOps v21.2 — Architecture

## Component overview

```
                     ┌──────────────────────────────────────┐
                     │            Browser (4200)            │
                     └──────────────────┬───────────────────┘
                                        │ HTTPS
                                        ▼
                     ┌──────────────────────────────────────┐
                     │       Frontend (nginx + Angular)     │
                     │  Loads /api/* → proxy_pass gateway   │
                     └──────────────────┬───────────────────┘
                                        │ HTTP
                                        ▼
        ┌───────────────────────────────────────────────────────────┐
        │            Gateway Service (Spring Boot 3.3, MVC)         │
        │  ┌─────────────────┐  ┌─────────────────────────────────┐ │
        │  │ Live endpoints  │  │ Proxy controller (downstream)   │ │
        │  │ /api/kubernetes │  │ /api/auth/*    → auth-service   │ │
        │  │ /api/runtime    │  │ /api/tasks     → task-service   │ │
        │  │ /api/platform   │  │ /api/ai-ops/*  → ai-ops-service │ │
        │  └─────────────────┘  └─────────────────────────────────┘ │
        └────┬──────────────────────────────────┬───────────────────┘
             │ shell out via                    │ HTTP
             │ /opt/nebula-tools/{kubectl,...}  │
             ▼                                  ▼
   ┌─────────────────────┐         ┌──────────────────────────┐
   │ Host runtime tools  │         │  9 microservices         │
   │ kubectl, docker,    │         │  auth, task, file,       │
   │ helm, terraform,    │         │  notification, ai-ops,   │
   │ trivy, argocd       │         │  devsecops, pipeline,    │
   └─────────────────────┘         │  observability, gitops,  │
                                   │  env-manager, terraform  │
                                   └──────────┬───────────────┘
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

| Service                       | Tech            | Port | Purpose                                     |
| ----------------------------- | --------------- | ---- | ------------------------------------------- |
| gateway-service               | Spring MVC      | 8080 | API gateway, proxy, live infra endpoints    |
| auth-service                  | Spring Boot     | 8081 | User registration, dev-mode login           |
| task-service                  | Spring Boot     | 8082 | Task CRUD, publishes RabbitMQ events        |
| notification-service          | Spring Boot     | 8083 | Consumes events, persists notifications     |
| file-service                  | Spring Boot     | 8084 | File metadata + storage abstraction         |
| ai-ops-service                | Spring Boot     | 8085 | Wraps Python AI engine, runs diagnostics    |
| devsecops-service             | Spring Boot     | 8086 | Trivy scans, secret scanning                |
| pipeline-engine-service       | Spring Boot     | 8087 | CI/CD pipeline state                        |
| observability-service         | Spring Boot     | 8092 | Trace/metric/log aggregator                 |
| gitops-control-service        | Spring Boot     | 8093 | ArgoCD wrapper                              |
| environment-manager-service   | Spring Boot     | 8094 | Namespace/env lifecycle                     |
| terraform-studio-service      | Spring Boot     | 8096 | Terraform plan/apply orchestrator           |
| ai-engine                     | Python FastAPI  | 8095 | Anomaly detection, log clustering           |
| go-cache-service              | Go              | 8091 | High-performance cache layer                |
| go-event-worker               | Go              | —    | Background queue consumer                   |

## Data flow examples

### Frontend loads Kubernetes snapshot

```
Angular  ──GET /api/kubernetes/snapshot──▶  nginx
                                              │
                                              ▼
                                            gateway-service
                                              │
                                              │ KubernetesPlatformService
                                              │   .nodes()
                                              ▼
                                            shell out: kubectl get nodes -o json
                                              │
                                              ▼
                            transform into K8sSnapshot{cluster, resources, logs}
                                              │
                                              ▼
                                            return JSON
```

### Frontend creates a task

```
Angular  ──POST /api/tasks──▶  nginx ──▶  gateway-service (ProxyController)
                                                      │
                                                      │ rest.postForObject
                                                      ▼
                                                  task-service:8082
                                                      │
                                                      ▼
                                                  MongoDB.save(task)
                                                  RabbitMQ.publish(TaskCreated)
                                                      │
                                                      ▼
                                              notification-service consumes
```

## Configuration sources

| Setting               | Source                                      |
| --------------------- | ------------------------------------------- |
| Service URLs          | `config/platform.yml` (canonical)           |
| Gateway proxy targets | `backend/gateway-service/src/main/resources/application.yml` |
| Frontend API base     | `frontend/src/app/api.config.ts`            |
| Stack composition     | `docker-compose.yml`                        |
| Environment overrides | Container env vars (set in compose file)    |

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

This document describes **v21.2.0** topology. See `RELEASE_NOTES_v21.2.md`.
