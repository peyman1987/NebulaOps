# Troubleshooting Guide

## Docker BuildKit snapshot error on WSL

### Symptom

During the frontend image export phase Docker may fail with an error similar to:

```text
failed to prepare extraction snapshot "extract-...": parent snapshot ... does not exist: not found
```

This happens after the Angular build has completed and while Docker is exporting or unpacking the final image. It is
usually caused by a corrupted BuildKit cache or an inconsistent Docker Desktop snapshot store, not by Angular source
code.

### Recommended repair

Run:

```bash
./scripts/wsl/docker-cache-repair.sh
DOCKER_BUILDKIT=1 docker compose -p nebulaops -f infrastructure/docker-compose.yml build --no-cache frontend
docker compose -p nebulaops -f infrastructure/docker-compose.yml up -d
```

### Full clean rebuild

Use this only when the previous repair is not enough:

```bash
docker compose -p nebulaops -f infrastructure/docker-compose.yml down --remove-orphans
docker builder prune -af
docker buildx prune -af
docker image prune -af
DOCKER_BUILDKIT=1 docker compose -p nebulaops -f infrastructure/docker-compose.yml build --no-cache
docker compose -p nebulaops -f infrastructure/docker-compose.yml up -d
```

### If the project is under `/mnt/c` or `/mnt/d`

For better WSL and Docker performance, keep the repository inside the Linux filesystem:

```bash
mkdir -p ~/projects
cp -r /mnt/d/workspace/personal/portfolio/nebulaops-v13 ~/projects/nebulaops
cd ~/projects/nebulaops
```

Then rebuild from the Linux path.

### Docker Desktop reset option

If Docker still reports missing parent snapshots after pruning caches, restart Docker Desktop. If the error remains, use
Docker Desktop → Troubleshoot → Clean / Purge data. This is destructive for local Docker data, so export anything
important first.
