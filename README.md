# NebulaOps v19.1 — Terraform DevOps Control Plane

NebulaOps v19.1 è un progetto portfolio Cloud/DevOps local-first con Angular, microservizi, Docker Compose, Kubernetes,
Helm, Grafana, ArgoCD/GitLab flow e **Terraform integrato nella root del repository**.

## Cosa include

- Frontend Angular cockpit con login demo e tab operativi.
- Backend/microservizi ereditati dalla v17.
- Docker Compose per esecuzione locale.
- Kubernetes/Helm per scenario platform.
- Terraform per generare configurazioni locali e baseline.
- SVG architetturali aggiornati.
- Documentazione v19.1 aggiornata.

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

- `docs/README_V19.1_INDEX.md`
- `docs/V19.1_RELEASE_NOTES.md`
- `docs/TERRAFORM_V19.1_GUIDE.md`
- `docs/V19.1_FRONTEND_STYLE_GUIDE.md`
- `docs/diagrams/nebulaops-v19.1-terraform-control-plane.svg`

## Autore

Sviluppato da Peyman Eshghi Malayeri — 2024/2026 portfolio evolution.

## v19.1 AI Ops Center

- New `AI OPS` tab with futuristic cockpit UI.
- Spring Boot `ai-ops-service` plus Python FastAPI `ai-engine`.
- Visual RCA, realtime timeline, animated dependency graph and safe `AUTO FIX` remediation staging.
- See `docs/V19_1_AI_OPS_CENTER.md` and `docs/V19_1_RELEASE_NOTES.md`.
