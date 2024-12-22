# Project Metadata

| Field     | Value                              |
|-----------|------------------------------------|
| Project   | NebulaOps v20.3                    |
| Developer | Peyman Eshghi Malayeri             |
| Email     | peyman_em@yahoo.com                |
| Year      | 2024                               |
| Edition   | Professional DevOps Control Center |

NebulaOps v20.3 is a local-first platform engineering project for WSL/Linux execution. It demonstrates microservices,
Docker Engine operations, Kubernetes console workflows, Helm release management, Grafana/Prometheus observability,
RabbitMQ, Redis, MongoDB and a modern Angular frontend.

## v20.3 AI Ops Center

- New `AI OPS` tab with futuristic cockpit UI.
- Spring Boot `ai-ops-service` plus Python FastAPI `ai-engine`.
- Visual RCA, realtime timeline, animated dependency graph and safe `AUTO FIX` remediation staging.
- See `docs/V19_1_AI_OPS_CENTER.md` and `docs/V19_1_RELEASE_NOTES.md`.

## Documentation patch status

Versione documentale corrente: **v20.3**.

Aggiornati:

- root `README.md`
- `START_HERE.md`
- `docs/README_V19_3_INDEX.md`
- `docs/V19_3_RELEASE_NOTES.md`
- `docs/V19_3_DEVSECOPS_MODULE.md`
- `docs/V19_3_KUBERNETES_VISUAL_CLUSTER.md`
- `docs/V19_3_AI_OPS_CENTER.md`
- `docs/V19_3_FRONTEND_STYLE_GUIDE.md`
- `docs/V19_3_DIAGRAMS.md`
- `docs/diagrams/README.md`
- SVG v20.3 in `docs/diagrams` e `frontend/src/assets`

## v20.3 Corrected - Home Feature Launcher

La home ora include un Command Center con tasti grandi per aprire rapidamente Grafana, ArgoCD, Prometheus e i moduli
interni AI OPS, Kubernetes Visual Cluster, Security, Helm e Observability.

Documentazione: `docs/V19_3_HOME_FEATURE_LAUNCHER.md`.

release_patch: v20.3-containers-openlens-refresh
features_added:

- Docker Desktop-like runtime console
- OpenLens-like Kubernetes workload console
- INFRA links for Docker/OpenLens internal consoles

## v20.3 Dynamic Runtime Patch

- release_patch: v20.3-live-runtime-services
- gateway_endpoints: /api/platform/observability, /api/platform/gitops, /api/platform/devsecops,
  /api/platform/environments
- go_cache_stats: /cache/stats
- frontend_style: Aurora Glass enterprise refresh
