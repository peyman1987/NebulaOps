# NebulaOps v16 Diagram Guide

The v16 diagrams are functional 3D SVGs, not decorative placeholders. Each file shows one specific operational flow with
directional arrows, lane labels, color-coded paths and animated movement.

## Reading the flows

- Solid cyan arrows: HTTP/API control requests.
- Purple dashed arrows: asynchronous events or queue messages.
- Green dotted arrows: metrics, logs and observability feedback.
- 3D boxes: deployable components, tools, dashboards or runtime services.

## Main diagrams

| File                                         | Purpose                                                       |
|----------------------------------------------|---------------------------------------------------------------|
| `diagrams/request-flow-sequence.svg`         | Browser click to gateway, services, data/event and telemetry. |
| `diagrams/runtime-architecture.svg`          | Docker Compose runtime plus Kubernetes/Helm control plane.    |
| `diagrams/observability-grafana-flow.svg`    | Prometheus scrape, Grafana provisioning and operator loop.    |
| `diagrams/messaging-cache-flow.svg`          | MongoDB, Redis, RabbitMQ and Go worker data path.             |
| `diagrams/frontend-operations-dashboard.svg` | FE tabs mapped to real APIs and control panels.               |
| `diagrams/kubernetes-helm-view.svg`          | OpenLens-like resource flow and YAML apply path.              |
| `diagrams/gitlab-argocd-flow.svg`            | CI/CD and GitOps deployment lifecycle.                        |
| `diagrams/service-port-map.svg`              | Browser ports and Docker network DNS map.                     |

These SVGs are designed for README/docs use and can be opened directly in a browser.
