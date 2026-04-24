# NebulaOps v23.2 Release Notes

## Objective
NebulaOps v23.2 focuses on turning the existing modules into operational consoles backed by runtime integrations only. The frontend remote modules do not render seeded, static or mock rows. Empty/error states explicitly show that the live source returned no data or that the dependency is unavailable.

## Docker Desktop
- Expanded the remote endpoint set to Docker engine status, containers, images, volumes, networks, stats and events.
- Added `/api/runtime/docker/status` with explicit states for missing socket, permission denied, stopped/unreachable Docker Desktop and reachable Docker Engine API.
- Docker collections now include `toolStatus`, `realDataOnly`, item count and socket diagnostics instead of inferring unavailable state from empty arrays.

## OpenLens Kubernetes
- Expanded the live sources to pods, deployments, services, ingress, configmaps, statefulsets, nodes, namespaces, events and Helm releases.
- Added `/api/kubernetes/cluster` alias for a stable OpenLens entrypoint.
- Existing action surface remains available: logs, describe, YAML read/update, restart, scale, cordon, drain, apply and delete YAML.

## INFRA Hub
- Reworked the remote configuration so INFRA Hub is no longer a Docker clone.
- Added `/api/platform/infra/*` endpoints for platform services, runtime ports, reverse proxy routes, Keycloak, RabbitMQ, Redis, MongoDB and observability probes.
- Runtime ports are derived from live Docker container data. Service and route health are active HTTP probes.

## Task Management
- Extended the remote source set to board, audit trail, RabbitMQ task events, notifications, release links and policy approvals.
- The module is positioned as an enterprise workflow board connected to releases, incidents and approvals.

## Observability
- Added incident timeline as first-class source: `/api/observability/incidents/timeline`.
- Timeline correlates live audit, task, notification, Loki and Tempo data using available correlation identifiers.
- Remote source set now prioritizes incident timeline, service health, Prometheus, Loki, Tempo, RabbitMQ, audit and notifications.

## Release Center and Progressive Delivery
- Release Center now focuses on release lifecycle, policy gates, approvals, GitOps sync, rollout history and release audit.
- Progressive Delivery now focuses on Argo Rollouts-style rollout operations, canary/blue-green overview, Argo CD applications, analysis runs, experiments and rollout audit.
- The two modules are linked through shared release, policy and rollout sources without duplicating responsibilities.

## Policy Center
- Expanded to governance summary, OPA policies, policy decisions, approval workflow, violations, release gates, DevSecOps findings and governance audit.
- Keeps release blocking and DevSecOps policy context together.

## Identity Admin
- Added realm status endpoint: `/api/identity/realms/{realm}/status`.
- Added Redis cache status endpoint: `/api/identity/cache/status`.
- Remote now exposes realm status, users, groups, roles and Redis identity cache state with explicit unavailable-state messages.

## Validation performed in this package
- Angular production build completed successfully.
- Classic micro-frontend verification completed successfully for all remotes.
- Maven compile could not be executed in this sandbox because the `mvn` binary is not installed. The package keeps standard Maven project structure and should be validated locally with `cd backend && mvn -DskipTests compile`.


## Platform Catalog & Service Registry

- Added a new `platform-catalog` micro frontend.
- Added gateway endpoint family `/api/platform/catalog/**`.
- Centralized MFE/MBE metadata, ports, endpoint links, owners, dependencies, Docker/Kubernetes mappings, OpenAPI links and operational links.
- Catalog rows are enriched by live probes and explicit runtime states; no frontend mock/static operational data is introduced.
- Shared live-only remotes now refresh endpoint groups concurrently for better perceived performance.

## Incident Command Center

- Added a dedicated SRE command-center MFE at `/remotes/incident-command-center/`.
- Added gateway aggregation under `/api/incidents/command-center/**`.
- The section correlates live incidents, observability timeline, impacted services, Loki logs, Prometheus metrics, Tempo traces, notifications, generated tasks, release/rollback context and Kubernetes pod/log context.
- The export endpoint returns a technical Markdown report generated from live runtime sources only.
- No incident rows are seeded in the frontend; unavailable integrations are returned as explicit `live=false` source states.
