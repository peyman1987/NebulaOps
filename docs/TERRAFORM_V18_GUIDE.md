# Guida Terraform v18

Questa guida spiega come usare Terraform dentro NebulaOps v18 su una macchina personale, WSL o Linux.

## 1. Dove si trova Terraform

Terraform è ora nella root:

```bash
terraform/
├── main.tf
├── variables.tf
├── outputs.tf
├── examples/local-kind/terraform.tfvars
└── modules/
```

È mantenuta anche una copia in `infrastructure/terraform/` per compatibilità con la struttura precedente.

## 2. Inizializzazione

```bash
cd terraform
terraform init
terraform fmt -recursive
terraform validate
```

## 3. Plan locale

```bash
terraform plan -var-file examples/local-kind/terraform.tfvars
```

Oppure dalla root:

```bash
./scripts/terraform/plan-local.sh
```

## 4. Apply locale

```bash
./scripts/terraform/apply-local.sh
```

Terraform genera file locali usati dal progetto:

- `infrastructure/kind/generated-nebulaops-v18.yaml`
- `infrastructure/kubernetes/namespace.generated.yaml`
- `infrastructure/kubernetes/security-baseline.generated.yaml`
- `infrastructure/helm/nebulaops/values.v18.generated.yaml`
- `infrastructure/argocd/v18-generated-note.yaml`

## 5. Esempio completo: nuovo ambiente locale

```bash
./scripts/terraform/apply-local.sh
kind create cluster --config infrastructure/kind/generated-nebulaops-v18.yaml
./scripts/local-up.sh
./scripts/helm-install-local.sh
```

## 6. Esempio: cambiare namespace

Modifica `terraform/examples/local-kind/terraform.tfvars`:

```hcl
cluster_name = "nebulaops-v18"
namespace    = "nebulaops-dev"
target       = "kind"
```

Poi esegui:

```bash
./scripts/terraform/apply-local.sh
```

## 7. Esempio: usare il FE

1. Avvia il progetto con `./scripts/local-up.sh`.
2. Apri il frontend.
3. Login: `admin/admin`.
4. Vai nel tab **TERRAFORM**.
5. Seleziona `local-kind`, `observability`, `gitops-bootstrap` o `security-baseline`.
6. Premi `Plan selected` per vedere il comando e il piano simulato.

## 8. Note importanti

Questa versione è progettata per portfolio e macchina personale. Non salva segreti reali nello state. Per produzione
servirebbero backend remoto, locking, policy enforcement e secrets manager.
