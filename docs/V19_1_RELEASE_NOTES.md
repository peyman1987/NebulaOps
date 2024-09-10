# NebulaOps v19.3 Release Notes

## Headline

v19.3 introduces **AI Ops Center** as the main product feature: a futuristic incident cockpit that combines realtime
events, visual root cause analysis, dependency graph animation, Kubernetes explainability and safe auto-fix staging.

## New features

- New frontend tab: `AI OPS`.
- Operational AI chat with log / Kubernetes incident prompt.
- Realtime incident timeline with severity colors.
- Visual Root Cause Analysis panel with confidence, blast radius and suggested remediation.
- Animated dependency graph with pulsing red crash node and beam connections across services.
- `AUTO FIX` action that stages the AI generated remediation patch.
- Generated Kubernetes YAML / Helm-ready patch preview.
- New Spring Boot microservice: `backend/ai-ops-service`.
- New Python FastAPI AI engine: `ai-engine`.
- Gateway route: `/api/ai-ops/**`.
- Docker Compose wiring for `ai-engine` and `ai-ops-service`.

## Demo incident

Default scenario: `gateway-service` pod crash / readiness failure.

Expected cockpit effect:

1. Gateway node becomes red and pulses.
2. Error propagation beams show impact to frontend, task-service and notification-service.
3. RCA explains likely probe / image / rollout cause.
4. AI generates a Kubernetes Deployment YAML patch.
5. `AUTO FIX` stages the remediation in demo-safe mode.

## Ports

| Component         | Port |
|-------------------|-----:|
| Gateway           | 8080 |
| AI Ops Service    | 8085 |
| AI Engine FastAPI | 8095 |
| Frontend          | 4200 |

## Validation

Run:

```bash
docker compose up --build
curl http://localhost:8085/api/ai-ops/playbook
curl http://localhost:8095/health
```

Then open `http://localhost:4200`, login with `admin/admin`, and select `AI OPS`.
