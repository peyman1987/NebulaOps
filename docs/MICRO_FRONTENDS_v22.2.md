# NebulaOps v22.2 — Expanded micro frontend architecture

Questa revisione mantiene il **menu laterale nella shell host** e aumenta la granularita dei micro frontend.

## Principio architetturale

- La shell Angular (`frontend`, porta 4200) gestisce login Keycloak, sessione, app launcher modal, layout e menu laterale.
- Ogni micro frontend remoto è un'app indipendente esposta da un container dedicato con `remoteEntry.js`.
- I remote non possiedono la navigazione globale: ricevono token/sessione dalla shell e renderizzano solo il proprio dominio.

## Remote catalog

| Remote | Container | Porta | Dominio |
|---|---|---:|---|
| Docker Desktop | `mfe-docker-desktop` | 4211 | Container, immagini, volumi, reti e azioni runtime Docker via gateway. |
| OpenLens Kubernetes | `mfe-openlens-kubernetes` | 4212 | Vista cluster, namespace, workload, service, eventi e Helm releases. |
| Task Management | `mfe-task-management` | 4213 | Backlog, priorità, ownership, eventi RabbitMQ e notifiche. |
| Observability | `mfe-observability` | 4214 | Grafana, Prometheus, Loki, Tempo, OpenTelemetry e KPI piattaforma. |
| CI/CD + GitOps | `mfe-cicd-gitops` | 4215 | Pipeline, GitLab opzionale, Helm, ArgoCD e promotion flow. |
| Terraform Studio | `mfe-terraform-studio` | 4216 | Plan, validate, apply locale, ambienti e moduli Terraform. |
| DevSecOps | `mfe-devsecops` | 4217 | Vulnerabilità, secret posture, Trivy, policy e hardening. |
| AI Ops | `mfe-ai-ops` | 4218 | RCA, insight operativi, anomaly hints e assistente piattaforma. |
| FinOps Cost | `mfe-finops-cost` | 4219 | Cost analytics, resource spend, forecast e ottimizzazione. |

## Modalita di avvio

```bash
./scripts/wsl/start.sh
./scripts/wsl/health.sh
```

Con SSO tools:

```bash
./scripts/wsl/start.sh --with-sso-proxy
```

## Nota UX

Il menu laterale resta sempre visibile nella shell, anche quando cambia remote. Questo evita perdita di contesto e impedisce che ogni micro frontend replichi navigazione, sessione o app bar.
