# NebulaOps v22.2 — Verified package report

## Scope

This package is the verified v22.2 build with expanded micro frontend architecture and persistent shell side navigation.

## Micro frontend topology

- Shell host: `frontend` on `localhost:4200`.
- Independent remotes:
  - Docker Desktop: `mfe-docker-desktop` on `localhost:4211`.
  - OpenLens Kubernetes: `mfe-openlens-kubernetes` on `localhost:4212`.
  - Task Management: `mfe-task-management` on `localhost:4213`.
  - Observability: `mfe-observability` on `localhost:4214`.
  - CI/CD + GitOps: `mfe-cicd-gitops` on `localhost:4215`.
  - Terraform Studio: `mfe-terraform-studio` on `localhost:4216`.
  - DevSecOps: `mfe-devsecops` on `localhost:4217`.
  - AI Ops: `mfe-ai-ops` on `localhost:4218`.
  - FinOps Cost: `mfe-finops-cost` on `localhost:4219`.

The shell owns Keycloak login, side navigation, App Bar modal, version badge and remote loading. Remotes expose only domain-specific custom elements and can be deployed independently.

## Validation checklist

The following checks are included in `./scripts/wsl/preflight-v22.2.sh`:

- `scripts/validate-package.py`
- `scripts/validate-yaml.py` for Compose, GitLab CI and ArgoCD YAML
- `bash -n` on all scripts
- remote manifest and `remoteEntry.js` verification
- Node syntax checks on remote entries and frontend tools
- Python syntax check on AI engine
- Go tests for cache service and event worker

## Runtime verification

Run from WSL/Docker host:

```bash
./scripts/wsl/preflight-v22.2.sh
./scripts/wsl/stop.sh
./scripts/wsl/start.sh
./scripts/wsl/health.sh
```

For tool UI SSO:

```bash
./scripts/wsl/stop.sh
./scripts/wsl/start.sh --with-sso-proxy
./scripts/wsl/health.sh
```

## Notes

Docker and Maven runtime build validation require a host with Docker daemon and network access to the container registries. The package uses multi-stage Dockerfiles so Spring services compile during `docker compose up --build` without requiring local `target/*.jar` files.
