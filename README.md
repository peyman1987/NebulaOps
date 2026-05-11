# NebulaOps v23.4

NebulaOps is a local cloud-operations platform composed of an Angular shell, same-origin micro frontend bundles, Spring Boot services, Go runtime helpers, an AI engine, Keycloak, RabbitMQ, MongoDB, Redis, Prometheus, Grafana, Loki and Tempo.

The v23.4 local runtime uses a single browser-facing origin:

```text
http://nebulaops.localhost
```

The frontend Nginx image is the public entry point. It serves the shell, serves the static MFE standalone pages and `remoteEntry.js` files, and proxies API/platform routes to the internal Docker network. This removes the old browser-facing MFE port model and keeps token, redirect and browser storage behavior under one origin.

## Start

```bash
cd nebulaops-v23.4
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

## v23.4 release identity

The release identity is aligned across documentation, Angular package metadata, Maven coordinates, Docker Compose project names, WSL scripts, preflight reports and frontend runtime assets:

```text
v23.4 / 23.4.0 / nebulaops-v23-4
```

Run the release guard before packaging or after extracting an archive over an existing workspace:

```bash
python3 scripts/validate-version-alignment-v23.4.py
./scripts/wsl/smoke-version-alignment-v23.4.sh
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


## Platform Catalog & Service Registry

NebulaOps v23.4 now includes a dedicated **Platform Catalog & Service Registry** section at:

```text
/remotes/platform-catalog/
/api/platform/catalog
```

The catalog centralizes MFE, backend service, endpoint, port, owner, dependency, Docker, Kubernetes, OpenAPI, logs, metrics, traces, release and policy metadata. Runtime state is loaded from gateway probes, Docker and Kubernetes integrations; unavailable integrations are reported explicitly instead of being replaced by mock data.


## Incident Command Center

NebulaOps v23.4 now includes **Incident Command Center** at:

```text
/remotes/incident-command-center/
/api/incidents/command-center
```

This section unifies live incident records, incident timeline, impacted service health, Loki logs, Prometheus metrics, Tempo traces, notifications, generated tasks, runbooks, release rollback context and Kubernetes pod/log links. It is intentionally live-only: when AI Ops, Observability, Audit, Notification, Task, Release or Kubernetes sources are empty or unreachable, the UI displays explicit source state instead of seeded incident data.

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

Current documentation is aligned with the v23.4 same-origin runtime:

- `ARCHITECTURE.md`
- `docs/TECHNICAL_DOCUMENTATION.md`
- `docs/API_EXAMPLES.md`
- `docs/DEPLOYMENT_GUIDE.md`
- `docs/GITLAB_ARGOCD.md`
- `docs/TROUBLESHOOTING.md`
- `docs/WSL_GUIDE.md`

Legacy local URLs are not used for MFE standalone buttons or shell remote loading. Development-only commands may still use framework defaults internally, but the supported browser entry point is `http://nebulaops.localhost`.


## Observability & Audit Center

The v23.4 observability console is live-only. It reads service health, Prometheus, Loki, Tempo, RabbitMQ, task, notification and audit endpoints through `/api/observability/**`. It does not render seeded, sample or mock records; empty tables mean the runtime source returned no rows or was unavailable.


## Progressive Delivery Center

NebulaOps v23.4 includes a Progressive Delivery Center for live Argo Rollouts and Argo CD operations. It reads only runtime data from Kubernetes, Argo Rollouts and Argo CD through `/api/progressive-delivery/**`; empty states indicate no live records or an unreachable runtime source.

### APIForge on Kubernetes

NebulaOps v23.4 includes APIForge in the App Bar. The source is packaged as an extension under `extensions/apiforge/`. APIForge is deployed directly to Kubernetes and exposed on the local NodePort `31110`:

```bash
./scripts/wsl/deploy-apiforge-k8s.sh
# open http://localhost:31110/apiforge/
```

The deployment manifest is `extensions/apiforge/k8s/deployment.yml`; it creates the APIForge deployment, service, persistent volume claim and optional ingress path `/apiforge`.


## NebulaOps v23.4 Extensions

NebulaOps v23.4 now keeps only APIForge as Kubernetes-hosted extension under `extensions/`, styled with the same NebulaOps dark/glass UI language and exposed in the App Bar:

| Extension | Source | NodePort | Purpose |
|---|---:|---:|---|
| APIForge | `extensions/apiforge` | `31110` | Kubernetes-hosted REST, GraphQL and WebSocket API workspace |

Deploy APIForge:

```bash
./scripts/wsl/deploy-extensions-k8s.sh
```


## v23.4 real-extension policy

The v23.4 extensions do not ship operational records as artificial seed data. Runtime screens call Spring Boot MVC endpoints and render only live responses from Kubernetes, mounted runbook/SLO files or configured service APIs. Missing integrations are reported as `NOT_CONFIGURED`, `DEGRADED` or `UNAVAILABLE`.


## APIForge UI-controlled extension

NebulaOps v23.4 keeps only APIForge under `extensions/`. The core platform starts first; APIForge can then be started from the UI:

```bash
./scripts/wsl/start.sh --rebuild
```

Open the NebulaOps APP BAR and select **APIForge Control**. The control screen can start, stop and inspect the APIForge Kubernetes deployment.

APIForge is served through the gateway after startup:

```text
http://nebulaops.localhost/apiforge/
```

Optional legacy auto-start remains available:

```bash
./scripts/wsl/start.sh --rebuild --with-extensions-k8s
```


## v23.4 UI-controlled extensions

This package installs only the selected extension set:

```text
extensions/apiforge
extensions/kubebridge
extensions/contract-hub
```

The APP BAR is reserved for platform services. The separate EXTENSIONS button exposes exactly these three installed extensions and supports:

```text
Start
Stop
Restart
Status
Open
```

Default startup keeps extensions UI-controlled:

```bash
./scripts/wsl/start.sh --rebuild
```

Optional CLI auto-deploy remains available:

```bash
./scripts/wsl/start.sh --rebuild --with-extensions-k8s
./scripts/wsl/deploy-extensions-k8s.sh --only apiforge
./scripts/wsl/deploy-extensions-k8s.sh --only kubebridge
./scripts/wsl/deploy-extensions-k8s.sh --only contract-hub
```

The extensions must not generate mock records or static operational data. Missing integrations are surfaced through explicit runtime states.


## AI Engine v23.4

The AI Engine uses Anthropic Messages API when `ANTHROPIC_API_KEY` is configured. When the key is absent or the provider is unreachable, the service returns an explicit `LLM_UNAVAILABLE` fallback response and does not fabricate RCA data.


## NebulaOps v23.4 Operational Issues Dashboard

Version 23.4 introduces a dedicated Operational Issues Dashboard for live runtime troubleshooting. The shell now includes `/remotes/operational-issues/`, backed by the gateway endpoints `/api/platform/issues`, `/api/platform/issues/summary`, and `/api/platform/issues/{id}/evidence`.

The dashboard aggregates Docker Engine, Kubernetes, Helm and extension-control health into one SRE view with explicit source states when a runtime source is unavailable.

## NebulaOps v23.4 Safe Action Plan

Version 23.4 now protects disruptive Docker, Kubernetes and Helm operations with a Safe Action Plan gate. The UI plans the action first, displays impacted resources, dependencies, reversibility, risk, command/API details and an exact confirmation phrase, then calls `/api/platform/actions/execute` only after explicit confirmation.

Safe Action Plan endpoints:

```text
POST /api/runtime/docker/actions/plan
POST /api/kubernetes/actions/plan
POST /api/platform/actions/execute
```

Protected operations include Docker prune, container kill/remove/restart, project stop/restart, pod delete/restart, workload rollout restart/scale, Helm rollback/uninstall and YAML apply/delete. Plans are built from live runtime evidence only.

## v23.4 Extension Async Control Plane

NebulaOps v23.4 starts and restarts installed extensions through an asynchronous control-plane model. `POST /api/extensions/{slug}/start` returns `202 Accepted` with an `operationId`; the UI polls `/api/extensions/operations/{operationId}` and reads `/api/extensions/{slug}/events` for live phase updates.

The visible phases are: Starting, Pulling image, Applying Kubernetes manifests, Waiting for rollout, Probing endpoint, Ready, Failed and Timeout. These phases are generated by the live deployment workflow and are not mock/static UI rows.

Smoke verification:

```bash
./scripts/wsl/smoke-extension-async-control-plane-v23.4.sh
```

To verify the full real deployment flow:

```bash
NEBULAOPS_RUN_ASYNC_EXTENSION_START_SMOKE=1 ./scripts/wsl/smoke-extension-async-control-plane-v23.4.sh
```

## v23.4 Diagnostics Bundle Export

NebulaOps v23.4 includes a live support/debug export for runtime troubleshooting:

- `GET /api/runtime/diagnostics/bundle`
- `GET /api/runtime/diagnostics/bundle.zip`

The bundle is generated at request time from Docker, Kubernetes, Helm, gateway health, extension status, frontend remote verification and preflight output. It does not include seeded operational records or static runtime datasets.

## v23.4 E2E Runtime Smoke Tests

NebulaOps v23.4 now includes E2E smoke tests for runtime regressions:

```bash
./scripts/wsl/smoke-runtime-v23.4.sh
./scripts/wsl/smoke-docker-openlens-v23.4.sh
./scripts/wsl/smoke-extensions-v23.4.sh
```

The tests check that Docker Desktop and OpenLens open without browser console/runtime errors, `/api/extensions` does not return HTTP 504, extension start returns `202 Accepted` with `operationId`, Docker/Kubernetes endpoints return real runtime data or explicit unavailable states, core endpoints do not return HTML error pages, and UI sources do not generate mock operational rows.

The UI console smoke uses Chromium/Chrome through the Chrome DevTools Protocol. Set `CHROME_BIN` when the browser is not auto-detected.

