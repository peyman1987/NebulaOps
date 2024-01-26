# GitLab CI/CD and Argo CD GitOps

NebulaOps uses GitLab CI for build-time automation and Argo CD for runtime reconciliation.

![GitLab ArgoCD flow](diagrams/gitlab-argocd-flow.svg)

## Responsibility split

| Area                    | Owner                                            |
|-------------------------|--------------------------------------------------|
| Validate repository     | GitLab CI                                        |
| Run tests               | GitLab CI                                        |
| Build Docker images     | GitLab CI                                        |
| Store images            | GitLab Container Registry or compatible registry |
| Render/package Helm     | GitLab CI                                        |
| Deploy to Kubernetes    | Argo CD                                          |
| Detect drift            | Argo CD                                          |
| Reconcile desired state | Argo CD                                          |

## Files

```text
.gitlab-ci.yml
infrastructure/argocd/project.yaml
infrastructure/argocd/application.yaml
infrastructure/argocd/applicationset.yaml
scripts/gitlab-validate.sh
scripts/argocd-apply-local.sh
```

## Pipeline stages

```text
validate -> test -> build -> package -> deploy -> verify
```

## Local validation

```bash
./scripts/gitlab-validate.sh
python3 scripts/validate-package.py
```

## GitOps flow

1. Developer pushes code or opens a merge request.
2. GitLab validates docs, YAML, scripts and service code.
3. GitLab builds images and packages Helm metadata.
4. Argo CD watches the configured repository path.
5. Argo CD renders the Helm chart and syncs Kubernetes resources.
6. Prometheus/Grafana observe the deployed platform.

## Author

**Peyman Eshghi Malayeri**  
Email: peyman_em@yahoo.com  
Project Year: 2024
