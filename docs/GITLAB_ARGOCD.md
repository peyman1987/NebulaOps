# GitLab CI and Argo CD Guide

## Delivery model

NebulaOps uses a professional delivery structure:

1. GitLab CI validates code, scripts and packaging.
2. Images are built and tagged.
3. Helm values or image tags are updated.
4. Argo CD reconciles Kubernetes desired state from Git.

![GitLab and Argo CD flow](diagrams/gitlab-argocd-flow.svg)

## Pipeline stages

| Stage    | Purpose                                  |
|----------|------------------------------------------|
| validate | YAML, shell and project structure checks |
| test     | backend/frontend test hooks              |
| build    | container image build stage              |
| package  | Helm rendering and artifact checks       |
| deploy   | GitOps handoff through Argo CD assets    |

## Local validation

```bash
./scripts/gitlab-validate.sh
./scripts/helm-render.sh
./scripts/argocd-apply-local.sh
```

## Production recommendations

- use protected branches
- use semantic versioning or commit SHA image tags
- scan images before promotion
- store secrets outside Git
- promote through dev, staging and production overlays
