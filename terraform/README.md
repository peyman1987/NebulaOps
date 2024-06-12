# NebulaOps v18 Terraform

Questa cartella è volutamente nella root del progetto per essere immediatamente visibile.
Terraform non è solo documentato: genera manifest locali per Kind, namespace Kubernetes, note GitOps, baseline security
e app values.

## Comandi rapidi

```bash
cd terraform
terraform init
terraform fmt -recursive
terraform validate
terraform plan -var="cluster_name=nebulaops-v18" -var="namespace=nebulaops"
terraform apply -auto-approve
```

## Cosa produce

- `infrastructure/kind/generated-nebulaops-v18.yaml`
- `infrastructure/kubernetes/namespace.generated.yaml`
- `infrastructure/kubernetes/security-baseline.generated.yaml`
- `infrastructure/argocd/v18-generated-note.yaml`
- `infrastructure/observability/V18_OBSERVABILITY_NOTE.md`

## Flusso consigliato

1. Esegui Terraform per generare l'infrastruttura locale.
2. Avvia Docker Compose con `./scripts/local-up.sh`.
3. Installa Helm con `./scripts/helm-install-local.sh`.
4. Apri il FE e usa il tab **TERRAFORM** per vedere moduli, drift e comandi.
