# NebulaOps v18 — Terraform Edition

NebulaOps v18 introduce una versione più completa e presentabile del progetto portfolio: Terraform visibile nella root,
frontend cockpit più avanzato, documentazione riorganizzata e diagrammi SVG aggiornati.

## Novità principali

- **Terraform root-first**: cartella `terraform/` nella root con
  moduli `kind`, `apps`, `observability`, `gitops`, `security`.
- **Frontend cockpit v18**:
  tab `TERRAFORM`, `KUBERNETES`, `TASKS`, `HELM`, `OBSERVABILITY`, `CICD`, `SECURITY`, `FINOPS`, `BACKUPS`, `DOCS`.
- **Kubernetes console**: CRUD locale, YAML live editor, scale workload, filtri namespace/kind.
- **Terraform cockpit**: simulazione `plan`, comandi copiabili, stato moduli, drift e risorse.
- **FinOps**: stima costi mensili per local/VPS/backup/retention.
- **Backup & restore**: script dedicati e playbook FE.
- **SVG aggiornati**: architettura 3D/holographic con flow Terraform → Helm → K8s → Observability.

## Struttura nuova

```text
nebulaops-v18/
├── terraform/                         # Terraform visibile in root
│   ├── main.tf
│   ├── variables.tf
│   ├── outputs.tf
│   ├── examples/local-kind/terraform.tfvars
│   └── modules/{kind,apps,observability,gitops,security}/
├── infrastructure/terraform/           # copia compatibile per vecchi script
├── docs/diagrams/                      # SVG aggiornati
├── frontend/src/assets/                # SVG usato dal FE
└── scripts/terraform/                  # plan/apply local
```

## Comandi consigliati

```bash
./scripts/terraform/plan-local.sh
./scripts/terraform/apply-local.sh
./scripts/local-up.sh
./scripts/helm-install-local.sh
./scripts/smoke-test.sh
```

## Credenziali demo FE

- `admin/admin`
- `peyman/admin`
