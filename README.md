# NebulaOps v18 — Terraform DevOps Control Plane

NebulaOps v18 è un progetto portfolio Cloud/DevOps local-first con Angular, microservizi, Docker Compose, Kubernetes,
Helm, Grafana, ArgoCD/GitLab flow e **Terraform integrato nella root del repository**.

## Cosa include

- Frontend Angular cockpit con login demo e tab operativi.
- Backend/microservizi ereditati dalla v17.
- Docker Compose per esecuzione locale.
- Kubernetes/Helm per scenario platform.
- Terraform per generare configurazioni locali e baseline.
- SVG architetturali aggiornati.
- Documentazione v18 aggiornata.

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

- `docs/README_V18_INDEX.md`
- `docs/V18_RELEASE_NOTES.md`
- `docs/TERRAFORM_V18_GUIDE.md`
- `docs/V18_FRONTEND_STYLE_GUIDE.md`
- `docs/diagrams/nebulaops-v18-terraform-control-plane.svg`

## Autore

Sviluppato da Peyman Eshghi Malayeri — 2024/2026 portfolio evolution.
