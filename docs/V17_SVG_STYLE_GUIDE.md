# NebulaOps v17 SVG Style Guide

The v17 diagrams use a consistent professional SaaS visual language: dark canvas, glass panels, neon edge highlights,
animated flow paths, clear labels and readable technical grouping.

## Design rules

- Use English labels only.
- Every diagram must explain a real system responsibility or request path.
- Avoid vague labels such as “3D control plane”; prefer concrete terms such as “Runtime Operations”, “Gateway API”,
  “Kubernetes Workloads” and “Grafana Dashboards”.
- Use cyan for synchronous API traffic.
- Use violet for asynchronous events.
- Use green for metrics, health and logs.
- Keep animations subtle and functional.
- Ensure diagrams remain readable when printed or viewed without animation.

## Primary SVG files

- `docs/diagrams/v17-saas-flow-3d.svg`
- `docs/diagrams/v17-runtime-control-3d.svg`
- `docs/diagrams/runtime-architecture.svg`
- `docs/diagrams/request-flow-sequence.svg`
- `docs/diagrams/kubernetes-helm-view.svg`
- `docs/diagrams/observability-grafana-flow.svg`
- `docs/diagrams/gitlab-argocd-flow.svg`

## Label examples

| Weak label                              | Professional replacement        |
|-----------------------------------------|---------------------------------|
| Control plane 3D                        | Runtime Operations Console      |
| Docker/Kubernetes/Grafana local systems | Local Platform Runtime          |
| Cloud magic flow                        | Gateway-to-Service Request Flow |
| Metrics box                             | Prometheus Metrics Pipeline     |
