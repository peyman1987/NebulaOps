# NebulaOps v20.6 Real Backend Services

Questa release sostituisce i controller demo/statici con servizi runtime-driven.

## Cosa cambia

- `terraform-studio-service`: esegue `terraform init/plan/show/graph` nella working directory
  configurata (`TERRAFORM_WORKDIR`).
- `devsecops-service`: esegue `trivy fs` e secret scan reale via filesystem repository (`REPO_PATH`).
- `observability-service`: interroga Prometheus, Loki, Tempo e Grafana via HTTP.
- `gitops-control-service`: usa `argocd app get/sync/rollback` oppure verifica CRD ArgoCD via `kubectl`.
- `environment-manager-service`: legge namespace reali via `kubectl` e workspace Terraform reali.
- `pipeline-engine-service`: legge `.gitlab-ci.yml` reale dal repository e ne estrae stage/job.
- `ai-ops-service`: se l'AI engine non risponde, restituisce diagnostica live da `kubectl`/Docker invece di incidenti
  mock.

## Principio v20.6

Nessun controller deve inventare risorse, CVE, moduli Terraform, pipeline, namespace o stato GitOps. Se un tool non è
disponibile, la risposta deve contenere `live:false` o `UNAVAILABLE` con `toolStatus`, non dati finti.

## Tool opzionali da installare/montare nei container

- `kubectl`
- `terraform`
- `argocd`
- `trivy`
- Docker socket `/var/run/docker.sock`
- kubeconfig `.kube/config`

