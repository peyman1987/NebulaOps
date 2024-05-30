# NebulaOps v17 — Release Notes

NebulaOps v17 introduces a professional dark SaaS visual identity, animated SVG architecture diagrams and a refined
Angular operations dashboard. The release focuses on clarity, portfolio presentation quality and practical local
execution.

## Highlights

- Professional frontend demo login: `admin / admin`.
- Reworked SaaS-style hero view with animated infrastructure nodes.
- English-only UI and diagram labels.
- Rebuilt SVG diagrams that describe real technical flows instead of decorative placeholders.
- Docker, Kubernetes, Helm, Grafana, Observability and Security sections aligned with platform-engineering terminology.
- Grafana provisioning kept to one default Prometheus datasource to avoid restart loops.
- WSL/Linux scripts retained for local execution on a single machine.

## Main diagrams

- `docs/diagrams/v17-saas-flow-3d.svg` — user, frontend, gateway, services, data and observability flow.
- `docs/diagrams/v17-runtime-control-3d.svg` — local workstation, Docker runtime, Kubernetes/Helm operations and
  monitoring.
- `docs/diagrams/runtime-architecture.svg` — complete runtime topology.
- `docs/diagrams/request-flow-sequence.svg` — end-to-end task creation lifecycle.
- `docs/diagrams/observability-grafana-flow.svg` — metrics collection and dashboarding.

## Run

```bash
./scripts/wsl/start.sh
```

## Validate

```bash
curl -I http://localhost:4200
curl -I http://localhost:3000
./scripts/wsl/smoke-test.sh
```
