# NebulaOps v22.2 micro frontend architecture

v22.2 uses an Angular host shell with a persistent sidebar and remote micro frontends loaded through `remoteEntry.js`.

The sidebar is not duplicated inside the remotes: it remains the shell responsibility. Each remote exposes only its own functional surface and is deployable independently.

| Remote | Port | Responsibility |
|---|---:|---|
| Docker Desktop | 4211 | Container, image, volume, network and Docker runtime actions through the gateway. |
| INFRA Hub | 4220 | Infrastructure console catalog for observability, data, gateway, GitOps and platform access points. |
| OpenLens Kubernetes | 4212 | Cluster, namespace, workload, service, event and Helm release visibility. |
| Task Management | 4213 | Backlog, priorities, ownership, RabbitMQ events and notifications. |
| Observability | 4214 | Grafana, Prometheus, Loki, Tempo, OpenTelemetry and platform KPIs. |
| CI/CD + GitOps | 4215 | Pipelines, optional GitLab, Helm, ArgoCD and promotion flow management. |
| Terraform Studio | 4216 | Terraform plan, validate and local apply flows with environments and reusable modules. |
| DevSecOps | 4217 | Vulnerability findings, secret posture, Trivy scans, policies and hardening workflows. |
| AI Ops | 4218 | RCA, operational insights, anomaly hints and platform assistance. |
| FinOps Cost | 4219 | Cost analytics, resource spend, forecast and optimization reporting. |

