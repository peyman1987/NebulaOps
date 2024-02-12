# Deployment Guide

## Local deployment

Use Docker Compose for local execution:

```bash
./scripts/local-up.sh
./scripts/smoke-test.sh
```

## WSL deployment

Use the WSL scripts when running on Windows 11 with Ubuntu:

```bash
./scripts/wsl/check-wsl.sh
./scripts/wsl/start.sh
./scripts/wsl/smoke-test.sh
```

## Kubernetes deployment path

1. Build and push service images.
2. Update Helm values with image tags.
3. Render Helm templates.
4. Apply with Helm or let Argo CD reconcile from Git.

```bash
./scripts/helm-render.sh
./scripts/helm-install-local.sh
```

## Environment strategy

| Environment | Purpose                                    |
|-------------|--------------------------------------------|
| local       | Developer execution through Docker Compose |
| dev         | Shared Kubernetes test environment         |
| staging     | Release candidate validation               |
| production  | Hardened deployment with managed services  |

## Production notes

For production, MongoDB, Redis and RabbitMQ should normally run as managed services or dedicated stateful platform
components. Application pods should be stateless and horizontally scalable.
