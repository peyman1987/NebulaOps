> Enterprise Cloud-Native Portfolio Project  
> Developed by Peyman Eshghi Malayeri (2024)  
> Contact: peyman_em@yahoo.com

# Argo CD GitOps

This directory contains GitOps manifests for deploying NebulaOps with Argo CD.

## Files

- `project.yaml`: Argo CD AppProject with namespace/repository boundaries.
- `application.yaml`: single Helm-based Application for NebulaOps.
- `applicationset.yaml`: optional multi-environment ApplicationSet for `dev`, `staging`, `prod`.

## Quick install

```bash
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl apply -f infrastructure/argocd/project.yaml
kubectl apply -f infrastructure/argocd/application.yaml
```

For local testing, update `spec.source.repoURL` in `application.yaml` to your GitLab repository URL after pushing this
project.
