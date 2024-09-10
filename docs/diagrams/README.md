# NebulaOps v19.3 — Diagram Index

Questa cartella contiene i diagrammi SVG della release **v19.3** e gli asset legacy mantenuti per storico.

## v19.3 primary diagrams

| File                                            | Purpose                                                                                                |
|-------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| `nebulaops-v19-3-devsecops-module.svg`          | DevSecOps Module: SECURITY, COMPLIANCE, VULNERABILITIES, scanner pipeline, CVE dashboard e risk score. |
| `nebulaops-v19-3-kubernetes-visual-cluster.svg` | Kubernetes Visual Cluster: topology live, pod drilldown, animated traffic, CPU/RAM bars.               |
| `nebulaops-v19-3-ai-ops-architecture.svg`       | AI Ops Center: RCA cockpit, logs/anomaly pipeline, AI engine e safe AUTO FIX flow.                     |

## Platform diagrams

| File                             | Purpose                                      |
|----------------------------------|----------------------------------------------|
| `runtime-architecture.svg`       | Complete runtime topology.                   |
| `request-flow-sequence.svg`      | End-to-end request lifecycle.                |
| `messaging-cache-flow.svg`       | RabbitMQ and Redis responsibilities.         |
| `observability-grafana-flow.svg` | Prometheus and Grafana metrics pipeline.     |
| `kubernetes-helm-view.svg`       | Kubernetes workloads and Helm release model. |
| `gitlab-argocd-flow.svg`         | CI/CD and GitOps delivery flow.              |
| `service-port-map.svg`           | Local service and port map.                  |

All diagrams are standalone SVG files with inline styling and animation. No external assets are required.

## Home Feature Launcher SVG

- `docs/diagrams/nebulaops-v19-3-home-feature-launcher.svg`
- `frontend/src/assets/nebulaops-v19-3-home-feature-launcher.svg`

Rappresenta i collegamenti dalla Home verso Grafana, ArgoCD, Prometheus e moduli interni NebulaOps v19.3.
