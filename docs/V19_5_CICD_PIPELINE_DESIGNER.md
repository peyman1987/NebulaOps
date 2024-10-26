# NebulaOps v19.5 — CI/CD Pipeline Designer

v19.5 introduces a portfolio-grade CI/CD Pipeline Designer with a visual drag-and-drop canvas and backend contract for
saving pipeline definitions.

## Frontend

- Canvas drag & drop stages:
    - Build
    - Test
    - Security Scan
    - Docker Build
    - Helm Deploy
    - Smoke Test
- Animated connector line across the pipeline graph.
- Realtime status badges: success, running, queued, blocked.
- Glowing pipeline nodes and stage inspector.
- Copyable GitLab-style YAML generated from the current canvas.

## Backend

New service: `backend/pipeline-engine-service`.

Endpoints:

- `GET /api/pipeline-engine/template` — returns default v19.5 stage map, GitLab YAML and ArgoCD metadata.
- `POST /api/pipeline-engine/designs` — saves a JSON/YAML pipeline design in memory for demo use.
- `GET /api/pipeline-engine/designs` — lists saved pipeline designs.
- `POST /api/pipeline-engine/gitlab/export` — returns generated `.gitlab-ci.yml` content.
- `POST /api/pipeline-engine/argocd/sync` — simulates an ArgoCD sync request after Helm Deploy.

## Integration

- Gateway route: `/api/pipeline-engine/**`.
- Docker Compose service: `pipeline-engine-service` on port `8087`.
- INFRA tab links the designer with Grafana, Redis Commander, Mongo Express, RabbitMQ, Prometheus, Gateway API, Pipeline
  Engine API, DevSecOps, Kubernetes, Observability and AI OPS.
