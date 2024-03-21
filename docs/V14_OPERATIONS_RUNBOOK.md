# NebulaOps v14 Operations Runbook

## Start

```bash
./scripts/local-up.sh
```

## Stop

```bash
./scripts/local-down.sh
```

## Verify

```bash
./scripts/verify-local.sh
./scripts/smoke-test.sh
```

## Useful endpoints

| Component           | URL                    |
|---------------------|------------------------|
| Angular frontend    | http://localhost:4200  |
| Gateway             | http://localhost:8080  |
| Prometheus          | http://localhost:9090  |
| Grafana             | http://localhost:3000  |
| RabbitMQ management | http://localhost:15672 |

## Common issues

### Docker daemon not reachable

Start Docker Desktop and enable WSL integration for your Ubuntu distribution.

### Slow build under `/mnt/c` or `/mnt/d`

Move the project to the WSL filesystem:

```bash
mkdir -p ~/projects
cp -r /mnt/d/workspace/personal/portfolio/nebulaops-v14 ~/projects/nebulaops-v14
```

### Kubernetes tab cannot connect

The app remains usable in local fallback mode. For real Kubernetes operations, generate a kubeconfig for a local cluster
and mount it to `.kube/config`.

```bash
./scripts/wsl/prepare-kubeconfig-for-docker.sh
```

### Logs are empty

The gateway reads Docker logs through `/var/run/docker.sock`. Ensure Docker Compose started the project
with `COMPOSE_PROJECT_NAME=nebulaops-v14` or use `./scripts/local-up.sh`.
