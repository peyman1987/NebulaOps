# NebulaOps v24.1 Release Notes

NebulaOps v24.1 is a runtime-stability, compact-UI and live-only operations release.

## Highlights

- Full release identifier alignment to `v24.1` / `24.1.0`.
- Docker image naming updated to the `nebulaops-v24-1-*` namespace.
- Fast non-blocking `/api/extensions` registry response retained.
- `/api/extensions/summary` hardened to avoid slow Kubernetes probes when no cached status exists.
- Deep extension status moved to explicit operator refresh via `/api/extensions/{slug}/status?refresh=true`.
- Extension UI continues to manage APIForge, KubeBridge and Contract Hub separately from the APP BAR.
- Compact shell density preserved for better visibility without browser zoom changes.
- Angular production build hardened with `--progress=false`.
- Live-only verification confirms that no mock/static operational records were introduced.

## Validation

Validated successfully:

- package validation
- YAML validation
- live-only runtime guard
- extension panel JavaScript syntax
- Angular production shell build
- 22 MFE same-origin classic remote bundle checks

Backend Maven compilation must be executed in the target WSL/Docker Desktop environment because this sandbox does not provide Maven or Docker daemon access.


## Docker/OpenLens Operational Console Upgrade

- Added Docker Compose project view from live Docker labels.
- Added Docker diagnostics, topology and prune preview endpoints.
- Added Docker container `top`, `kill` and image `history` operations.
- Added Kubernetes problem detector using live `kubectl` data.
- Added OpenLens coverage for DaemonSets, Jobs, CronJobs, masked Secrets, PVCs and StorageClasses.
- Added workload detail endpoint with JSON, YAML, describe output and events.
- Added server-side YAML dry-run before apply/delete workflows.
- Secret values are masked by the gateway before reaching the UI.
- Fixed duplicate Kubernetes event entries in derived log output.

## Docker/OpenLens Operational Console Step 2

- Added Docker Compose project detail and project-level start/stop/restart/log aggregation endpoints.
- Added Docker published host-port conflict detection from live Docker Engine metadata.
- Added OpenLens port-forward manager with create/list/stop operations backed by real `kubectl port-forward` processes.
- Added Helm status, values, history, rollback and uninstall endpoints.
- Added RBAC summary and `kubectl auth can-i` explorer endpoints.
- Hardened port-forward cleanup, kubeconfig temp-file handling and RBAC unavailable-state reporting.

## Docker/OpenLens Operational Console Step 3

- Added Docker resource-pressure view from live Docker stats.
- Added Docker build-cache and volume-usage views from live Docker `/system/df` output.
- Added Docker project risk summary derived from Compose labels, container health and published-port conflicts.
- Added container detail, health and filesystem change endpoints backed by Docker Engine API.
- Added OpenLens storage summary for PVCs, PVs, StorageClasses and ResourceQuotas.
- Added OpenLens network summary for Services, Endpoints, Ingress and NetworkPolicies.
- Added namespace quota/limit-range visibility.
- Added workload rollout-status endpoint.
- Added pod diagnostics endpoint with pod JSON, describe, current logs, previous logs and related events.
- Re-ran the live-only guard to confirm no mock/static operational records were introduced.

## Docker/OpenLens Operational Console Step 4

- Added Docker network-exposure audit from live published port bindings.
- Added Docker restart-policy and healthcheck coverage audit from live container inspect data.
- Added Docker environment risk audit with secret-like values masked at the gateway.
- Added Docker mount risk audit for bind mounts, host-root mounts and Docker socket mounts.
- Added OpenLens security summary for pod host namespaces, hostPath volumes, privileged containers, privilege escalation, added capabilities and missing requests/limits.
- Added OpenLens network policy inventory, ingress TLS audit, service endpoint readiness matrix and events timeline.
- Re-ran live-only runtime verification and v24.1 static preflight.

## Docker/OpenLens Troubleshooting & Root Cause Upgrade

Added within v24.1 without changing the public release number.

### Docker Desktop

- Added Compose project health report.
- Added Compose project failure analysis.
- Added project startup order from Docker Engine metadata.
- Added project dependency map across containers, ports, networks and mounts.
- Added project/container log analysis from live Docker logs.
- Added bounded Docker operations timeline from Docker Engine events.

### OpenLens Kubernetes

- Added pod root-cause endpoint.
- Added workload root-cause endpoint.
- Added service connectivity analyzer.
- Added ingress connectivity analyzer.
- Added namespace connectivity summary.
- Added workload and namespace dependency maps.

### Policy

- No bundled operational records were added.
- Runtime views remain live-only and return explicit unavailable/not-configured states instead of fallback data.


## Safe Action Plan

Disruptive Docker, Kubernetes and Helm actions now require a generated Safe Action Plan and exact operator confirmation before execution. Planning endpoints expose impacted resources, dependencies, reversibility, command/API details and LOW/MEDIUM/HIGH risk from live runtime evidence.

## Priority 4 — Extension Async Control Plane

- `POST /api/extensions/{slug}/start` now returns `202 Accepted` with an `operationId`.
- Added `GET /api/extensions/operations/{operationId}` for lifecycle polling.
- Added `GET /api/extensions/{slug}/events` for recent live operation events.
- Extended `GET /api/extensions/{slug}/diagnostics` with recent async events and operations.
- Updated the Extension Control Center to show lifecycle phases: Starting, Pulling image, Applying Kubernetes manifests, Waiting for rollout, Probing endpoint, Ready, Failed and Timeout.
- Added `scripts/wsl/smoke-extension-async-control-plane-v24.1.sh`.
