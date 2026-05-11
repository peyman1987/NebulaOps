# NebulaOps v23.4 micro frontend architecture

v23.4 uses an Angular host shell with a persistent side menu and remote micro frontends loaded through `remoteEntry.js`.

Il menu laterale non viene duplicato nei remote: resta responsabilita della shell. Ogni remote espone solo il contenuto della propria area funzionale ed e deployabile separatamente.

| Remote | Porta | Responsabilita |
|---|---:|---|
| Docker Desktop | 4211 | Container, immagini, volumi, reti e azioni runtime Docker via gateway. |
| OpenLens Kubernetes | 4212 | Vista cluster, namespace, workload, service, eventi e Helm releases. |
| Task Management | 4213 | Backlog, priorità, ownership, eventi RabbitMQ e notifiche. |
| Observability | 4214 | Grafana, Prometheus, Loki, Tempo, OpenTelemetry e KPI piattaforma. |
| CI/CD + GitOps | 4215 | Pipeline, GitLab opzionale, Helm, ArgoCD e promotion flow. |
| Terraform Studio | 4216 | Plan, validate, apply locale, ambienti e moduli Terraform. |
| DevSecOps | 4217 | Vulnerabilità, secret posture, Trivy, policy e hardening. |
| AI Ops | 4218 | RCA, insight operativi, anomaly hints e assistente piattaforma. |
| FinOps Cost | 4219 | Cost analytics, resource spend, forecast e ottimizzazione. |

