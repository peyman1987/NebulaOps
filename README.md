# NebulaOps v22.4

NebulaOps is a local cloud-operations platform composed of an Angular shell, same-origin micro frontend bundles, Spring Boot services, Go runtime helpers, an AI engine, Keycloak, RabbitMQ, MongoDB, Redis, Prometheus, Grafana, Loki and Tempo.

The v22.4 local runtime uses a single browser-facing origin:

```text
http://nebulaops.localhost
```

The frontend Nginx image is the public entry point. It serves the shell, serves the static MFE standalone pages and `remoteEntry.js` files, and proxies API/platform routes to the internal Docker network. This removes the old browser-facing MFE port model and keeps token, redirect and browser storage behavior under one origin.

## Start

```bash
cd nebulaops-v22.4
chmod +x scripts/wsl/*.sh scripts/*.sh
./scripts/wsl/start.sh --rebuild
./scripts/wsl/health.sh
```

Open:

```text
http://nebulaops.localhost
```

Optional SSO proxies for tool UIs can be enabled only after the core stack is healthy:

```bash
./scripts/wsl/start.sh --with-sso-proxy --rebuild
```

Optional GitLab CE can be started separately when CI/CD integration testing is needed:

```bash
./scripts/wsl/start.sh --with-gitlab
```

## Public local routes

| Route | Target |
| --- | --- |
| `/` | Angular shell |
| `/remotes/<mfe>/` | Static standalone MFE page copied into the frontend image |
| `/remotes/<mfe>/remoteEntry.js` | Static custom-element bundle used by the shell |
| `/api/**` | Gateway service and downstream Spring Boot / Go APIs |
| `/actuator/**` | Gateway actuator endpoints |
| `/keycloak/**` | Keycloak |
| `/grafana/**` | Grafana |
| `/prometheus/**` | Prometheus |

## Runtime components

| Area | Components |
| --- | --- |
| Frontend | Angular shell, custom-element MFE bundles, same-origin standalone pages |
| Backend APIs | Gateway, auth, task, notification, file, AI Ops, DevSecOps, pipeline, observability, GitOps, environment, Terraform, cost, release, policy and audit services |
| Data and messaging | MongoDB, RabbitMQ and Redis |
| Runtime helpers | Go cache service and Go event worker |
| Observability | Prometheus, Grafana, Loki, Tempo and OpenTelemetry Collector |
| Delivery assets | Helm chart, GitLab CI pipeline and Argo CD manifests |
| Local execution | Docker Compose with WSL-oriented scripts |

## Canonical diagrams

The maintained SVG set is intentionally aligned with the current reverse-proxy runtime:

| Diagram | Purpose |
| --- | --- |
| `docs/architecture-animated.svg` | Browser-facing same-origin runtime overview |
| `docs/diagrams/runtime-architecture.svg` | Runtime service topology |
| `docs/diagrams/gitlab-argocd-flow.svg` | Optional GitLab CI and Argo CD delivery flow |
| `docs/diagrams/messaging-cache-flow.svg` | MongoDB, RabbitMQ and Redis flow |
| `docs/diagrams/kubernetes-helm-view.svg` | Kubernetes and Helm deployment view |
| `docs/diagrams/request-flow-sequence.svg` | Browser to gateway request lifecycle |
| `docs/diagrams/service-port-map.svg` | Local route and port exposure map |

## Documentation

Current documentation is aligned with the v22.4 same-origin runtime:

- `ARCHITECTURE.md`
- `docs/TECHNICAL_DOCUMENTATION.md`
- `docs/API_EXAMPLES.md`
- `docs/DEPLOYMENT_GUIDE.md`
- `docs/GITLAB_ARGOCD.md`
- `docs/TROUBLESHOOTING.md`
- `docs/WSL_GUIDE.md`

Legacy local URLs are not used for MFE standalone buttons or shell remote loading. Development-only commands may still use framework defaults internally, but the supported browser entry point is `http://nebulaops.localhost`.


## Observability & Audit Center

The v22.4 observability console is live-only. It reads service health, Prometheus, Loki, Tempo, RabbitMQ, task, notification and audit endpoints through `/api/observability/**`. It does not render seeded, sample or mock records; empty tables mean the runtime source returned no rows or was unavailable.


## Progressive Delivery Center

NebulaOps v22.4 includes a Progressive Delivery Center for live Argo Rollouts and Argo CD operations. It reads only runtime data from Kubernetes, Argo Rollouts and Argo CD through `/api/progressive-delivery/**`; empty states indicate no live records or an unreachable runtime source.
