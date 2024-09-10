# NebulaOps v19.3 — Documentation Index

Questo indice è aggiornato per la release **v19.3 DevSecOps Module** e sostituisce gli indici legacy v18/v19.1/v19.2.

## Percorso consigliato

1. `README.md` — overview progetto e quick start.
2. `START_HERE.md` — comandi minimi per avvio locale.
3. `PROJECT_METADATA.md` — versione, stack e moduli inclusi.
4. `docs/V19_3_RELEASE_NOTES.md` — release notes ufficiali v19.3.
5. `docs/V19_3_DEVSECOPS_MODULE.md` — SECURITY / COMPLIANCE / VULNERABILITIES.
6. `docs/V19_3_KUBERNETES_VISUAL_CLUSTER.md` — topology Kubernetes live.
7. `docs/V19_3_AI_OPS_CENTER.md` — AI Ops Center e RCA cockpit.
8. `docs/V19_3_FRONTEND_STYLE_GUIDE.md` — stile FE, tab, animazioni e icone.
9. `docs/V19_3_DIAGRAMS.md` — inventario diagrammi e SVG.

## Diagrammi v19.3 principali

| Diagramma                 | File                                                          |
|---------------------------|---------------------------------------------------------------|
| DevSecOps Module          | `docs/diagrams/nebulaops-v19-3-devsecops-module.svg`          |
| Kubernetes Visual Cluster | `docs/diagrams/nebulaops-v19-3-kubernetes-visual-cluster.svg` |
| AI Ops Center             | `docs/diagrams/nebulaops-v19-3-ai-ops-architecture.svg`       |
| Runtime Architecture      | `docs/diagrams/runtime-architecture.svg`                      |
| GitLab / Argo CD flow     | `docs/diagrams/gitlab-argocd-flow.svg`                        |
| Kubernetes / Helm view    | `docs/diagrams/kubernetes-helm-view.svg`                      |

## Moduli v19.3

- `frontend` — cockpit Angular con tab AI OPS, Kubernetes Visual Cluster e DevSecOps.
- `backend/ai-ops-service` — foundation AI Ops / RCA / incident actions.
- `ai-engine` — FastAPI AI engine demo-safe.
- `backend/devsecops-service` — API security scans, compliance, CVE e risk score.

## Stato

- Documentazione aggiornata a **v19.3**.
- SVG v19.3 presenti in `docs/diagrams` e `frontend/src/assets`.
- Indici legacy mantenuti solo per storico.

- [Build Stabilization Patch](V19_3_BUILD_STABILIZATION.md)

## v19.3 Corrected - Home Feature Launcher

La home ora include un Command Center con tasti grandi per aprire rapidamente Grafana, ArgoCD, Prometheus e i moduli
interni AI OPS, Kubernetes Visual Cluster, Security, Helm e Observability.

Documentazione: `docs/V19_3_HOME_FEATURE_LAUNCHER.md`.
