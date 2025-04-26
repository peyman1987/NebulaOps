# NebulaOps v21.1 Runtime Tools

NebulaOps backend services are **live-only**. They do not return mock/static data.

If an endpoint returns:

```json
{"live": false, "toolStatus": {"stderr": "sh: 1: kubectl: not found", "exitCode": 127}}
```

it means the backend container cannot see the required CLI.

## Fix

Run from the project root:

```bash
./scripts/wsl/prepare-kubeconfig-for-docker.sh
./scripts/wsl/prepare-runtime-tools.sh
docker compose -p nebulaops-v21-1 -f docker-compose.yml up --build -d
```

The script copies available host tools into `.runtime-tools/`, which is mounted read-only into backend containers
at `/opt/nebula-tools`.

Required for Kubernetes/Docker live data:

```bash
docker --version
kubectl version --client
```

Optional integrations:

```bash
helm version
trivy --version
terraform version
argocd version --client
```

## Why this design

- no enterprise CLI downloads during Docker build
- no fake fallback data
- backend response is honest: `live:false` when a tool is missing
- host tools are reused from WSL/Docker Desktop
