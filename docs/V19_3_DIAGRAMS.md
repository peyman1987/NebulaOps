# NebulaOps v19.3 — Diagrams & SVG Inventory

Tutti i diagrammi principali della release v19.3 sono SVG standalone con CSS inline e animazioni leggere.

## Diagrammi v19.3

| File                                                          | Descrizione                                                                                              |
|---------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `docs/diagrams/nebulaops-v19-3-devsecops-module.svg`          | DevSecOps cockpit: Trivy, Docker image scan, SAST, secrets detection, CVE dashboard, compliance posture. |
| `docs/diagrams/nebulaops-v19-3-kubernetes-visual-cluster.svg` | Kubernetes Visual Cluster: nodes, pods, services, ingress, volumes e traffico realtime.                  |
| `docs/diagrams/nebulaops-v19-3-ai-ops-architecture.svg`       | AI Ops Center: log analysis, anomaly detection, RCA, auto-fix staging e FastAPI AI engine.               |
| `docs/architecture-animated.svg`                              | Diagramma architetturale principale usato nella documentazione.                                          |

## Diagrammi platform mantenuti

- `runtime-architecture.svg`
- `request-flow-sequence.svg`
- `messaging-cache-flow.svg`
- `observability-grafana-flow.svg`
- `kubernetes-helm-view.svg`
- `gitlab-argocd-flow.svg`
- `service-port-map.svg`

## Frontend assets

Gli stessi SVG v19.3 sono disponibili anche in:

```text
frontend/src/assets/
```

Questo permette al tab Docs/Architecture del FE di mostrare gli stessi diagrammi presenti in `docs/diagrams`.

## Home Feature Launcher SVG

- `docs/diagrams/nebulaops-v19-3-home-feature-launcher.svg`
- `frontend/src/assets/nebulaops-v19-3-home-feature-launcher.svg`

Rappresenta i collegamenti dalla Home verso Grafana, ArgoCD, Prometheus e moduli interni NebulaOps v19.3.
