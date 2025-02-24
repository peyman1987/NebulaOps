## v20.4.1 Build Fix

- Fixed frontend Docker build where `ng` was not available during production image build.
- Frontend Dockerfile now installs dev build tooling with `npm ci --include=dev` and invokes Angular through `./node_modules/.bin/ng`.

# NebulaOps v20.4 — Terraform DevOps Control Plane

NebulaOps v20.4 è un progetto portfolio Cloud/DevOps local-first con Angular, microservizi, Docker Compose, Kubernetes,
Helm, Grafana, Prometheus, Argo CD/GitLab flow e **Terraform integrato nella root del repository**.

## Cosa include

- Frontend Angular cockpit con login demo e tab operativi.
- Backend/microservizi Spring Boot, Go worker, MongoDB, RabbitMQ e Redis ereditati ed estesi dalla v17/v18.
- Docker Compose per esecuzione locale e supporto WSL.
- Kubernetes/Helm per scenario platform.
- Terraform per generare configurazioni locali e baseline.
- SVG architetturali aggiornati.
- Documentazione v20.4 aggiornata.

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
- `docs/diagrams/nebulaops-v20-2-devsecops-module.svg`

## Autore

Sviluppato da Peyman Eshghi Malayeri — 2024/2026 portfolio evolution.

## v20.4 AI Ops Center

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
- `docs/diagrams/nebulaops-v20-2-kubernetes-visual-cluster.svg`

## v20.4 DevSecOps Module

La v20.4 aggiunge i tab `SECURITY`, `COMPLIANCE` e `VULNERABILITIES` con radar animation, threat map, critical alerts,
animated risk score, Trivy/Docker/SAST/secrets/dependency scan simulation e CVE dashboard.

- `docs/V19_3_DEVSECOPS_MODULE.md`
- `docs/V19_3_RELEASE_NOTES.md`
- `docs/diagrams/nebulaops-v20-2-devsecops-module.svg`
- `backend/devsecops-service`

## v20.4 Documentation & diagrams patch

La documentazione ufficiale aggiornata della release è in `docs/README_V19_3_INDEX.md`.
I diagrammi SVG aggiornati sono in `docs/diagrams/` e includono DevSecOps, Kubernetes Visual Cluster e AI Ops Center.

## v20.4 Corrected Build Package

This package includes the DevSecOps module and the v20.4 stabilization patch:

- BuildKit Maven cache in all Spring service Dockerfiles.
- Retry-hardened Maven commands to reduce Maven Central timeout failures.
- Explicit Docker image names using `nebulaops-v20-2-*`.
- Backend Maven versions aligned to `20.4.0`.
- Docs, Markdown files, and SVG labels aligned to v20.4.

Recommended command:

```bash
DOCKER_BUILDKIT=1 docker compose build --parallel=false
docker compose up
```

## v20.4 Corrected - Home Feature Launcher

La home ora include un Command Center con tasti grandi per aprire rapidamente Grafana, ArgoCD, Prometheus e i moduli
interni AI OPS, Kubernetes Visual Cluster, Security, Helm e Observability.

Documentazione: `docs/V19_3_HOME_FEATURE_LAUNCHER.md`.

## v20.4 Highlights

- CI/CD Pipeline Designer with drag & drop canvas: Build, Test, Security Scan, Docker Build, Helm Deploy and Smoke Test.
- `pipeline-engine-service` for JSON/YAML saving, GitLab export and ArgoCD sync simulation.
- Restored `INFRA` tab with Grafana, ArgoCD, Prometheus, RabbitMQ and internal feature links.

See `docs/README_V20_1_INDEX.md`.

## NebulaOps v20.4

Adds Advanced Observability Stack, GitOps Control Plane, Multi-Environment Manager and Smart Terraform Studio. The INFRA
tab now opens Grafana, Redis Commander, Mongo Express, RabbitMQ, Prometheus, Loki, Tempo, OpenTelemetry Collector,
ArgoCD and all internal feature modules.

## v20.4 Containers refresh

This package includes a new `CONTAINERS` tab that integrates Docker Desktop-like runtime management and OpenLens-like
Kubernetes workload operations: containers, images, volumes, logs, terminal, pod scaling, rollout restart,
service/controller/ingress inspection and INFRA launchpad links.

## v20.4 Live Runtime Upgrade

La release v20.4 aggiunge un livello dinamico sopra i moduli enterprise della v19.x:

- UI restyling Aurora Glass per una dashboard più moderna e leggibile.
- Backend Gateway con nuovi endpoint `/api/platform/observability`, `/api/platform/gitops`, `/api/platform/devsecops`, `/api/platform/environments`.
- Observability, GitOps, DevSecOps e ambienti ora leggono dati runtime quando tool e servizi sono disponibili.
- Docker Desktop panel normalizza output reale Docker Engine per container, immagini e volumi.
- Go cache-service espone `/cache/stats` con statistiche Redis live.

Vedi `docs/V20_1_RELEASE_NOTES.md` per i dettagli.


## v20.4.3 NPM Build Fix

Frontend Docker build hardened: Node 20.41.1, stale lockfile removed, npm install fallback for Angular dependencies. See V20_2_3_NPM_FIX.md.
