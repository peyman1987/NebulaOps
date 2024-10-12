# NebulaOps v19.4 — Terraform DevOps Control Plane

NebulaOps v19.4 è un progetto portfolio Cloud/DevOps local-first con Angular, microservizi, Docker Compose, Kubernetes,
Helm, Grafana, Prometheus, Argo CD/GitLab flow e **Terraform integrato nella root del repository**.

## Cosa include

- Frontend Angular cockpit con login demo e tab operativi.
- Backend/microservizi Spring Boot, Go worker, MongoDB, RabbitMQ e Redis ereditati ed estesi dalla v17/v18.
- Docker Compose per esecuzione locale e supporto WSL.
- Kubernetes/Helm per scenario platform.
- Terraform per generare configurazioni locali e baseline.
- SVG architetturali aggiornati.
- Documentazione v19.4 aggiornata.

## Quick start

```bash
cp .env.example .env
./scripts/terraform/plan-local.sh
./scripts/terraform/apply-local.sh
./scripts/local-up.sh
./scripts/smoke-test.sh
```

Frontend: apri la porta configurata nel compose. Login demo: `admin/admin`.

## Terraform

La cartella principale è:

```text
terraform/
```

Comandi:

```bash
cd terraform
terraform init
terraform validate
terraform plan -var-file examples/local-kind/terraform.tfvars
terraform apply -auto-approve -var-file examples/local-kind/terraform.tfvars
```

## Documentazione

- `docs/README_V19_3_INDEX.md`
- `docs/V19_3_RELEASE_NOTES.md`
- `docs/TERRAFORM_V19_3_GUIDE.md`
- `docs/V19_3_FRONTEND_STYLE_GUIDE.md`
- `docs/diagrams/nebulaops-v19-4-devsecops-module.svg`

## Autore

Sviluppato da Peyman Eshghi Malayeri — 2024/2026 portfolio evolution.

## v19.4 AI Ops Center

- New `AI OPS` tab with futuristic cockpit UI.
- Spring Boot `ai-ops-service` plus Python FastAPI `ai-engine`.
- Visual RCA, realtime timeline, animated dependency graph and safe `AUTO FIX` remediation staging.
- See `docs/V19_3_AI_OPS_CENTER.md` and `docs/V19_3_RELEASE_NOTES.md`.

## Diagrammi principali

- `docs/diagrams/runtime-architecture.svg`
- `docs/diagrams/gitlab-argocd-flow.svg`
- `docs/diagrams/messaging-cache-flow.svg`
- `docs/diagrams/kubernetes-helm-view.svg`
- `docs/diagrams/request-flow-sequence.svg`
- `docs/diagrams/service-port-map.svg`
- `docs/diagrams/nebulaops-v19-4-kubernetes-visual-cluster.svg`

## v19.4 DevSecOps Module

La v19.4 aggiunge i tab `SECURITY`, `COMPLIANCE` e `VULNERABILITIES` con radar animation, threat map, critical alerts,
animated risk score, Trivy/Docker/SAST/secrets/dependency scan simulation e CVE dashboard.

- `docs/V19_3_DEVSECOPS_MODULE.md`
- `docs/V19_3_RELEASE_NOTES.md`
- `docs/diagrams/nebulaops-v19-4-devsecops-module.svg`
- `backend/devsecops-service`

## v19.4 Documentation & diagrams patch

La documentazione ufficiale aggiornata della release è in `docs/README_V19_3_INDEX.md`.
I diagrammi SVG aggiornati sono in `docs/diagrams/` e includono DevSecOps, Kubernetes Visual Cluster e AI Ops Center.

## v19.4 Corrected Build Package

This package includes the DevSecOps module and the v19.4 stabilization patch:

- BuildKit Maven cache in all Spring service Dockerfiles.
- Retry-hardened Maven commands to reduce Maven Central timeout failures.
- Explicit Docker image names using `nebulaops-v19-4-*`.
- Backend Maven versions aligned to `19.4.0`.
- Docs, Markdown files, and SVG labels aligned to v19.4.

Recommended command:

```bash
DOCKER_BUILDKIT=1 docker compose build --parallel=false
docker compose up
```

## v19.4 Corrected - Home Feature Launcher

La home ora include un Command Center con tasti grandi per aprire rapidamente Grafana, ArgoCD, Prometheus e i moduli
interni AI OPS, Kubernetes Visual Cluster, Security, Helm e Observability.

Documentazione: `docs/V19_3_HOME_FEATURE_LAUNCHER.md`.


## v19.4 Highlights

- CI/CD Pipeline Designer with drag & drop canvas: Build, Test, Security Scan, Docker Build, Helm Deploy and Smoke Test.
- `pipeline-engine-service` for JSON/YAML saving, GitLab export and ArgoCD sync simulation.
- Restored `INFRA` tab with Grafana, ArgoCD, Prometheus, RabbitMQ and internal feature links.

See `docs/README_V19_4_INDEX.md`.
