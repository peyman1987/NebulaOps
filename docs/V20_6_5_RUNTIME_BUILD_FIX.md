# NebulaOps v22.2 Runtime/Build Fix

This package fixes the repeated Java runtime failure:

```text
exec: "java": executable file not found in $PATH
```

## What changed

- All Spring Boot backend Dockerfiles now use `eclipse-temurin:21-jre` at runtime.
- Backend entrypoints call Java by absolute path: `/opt/java/openjdk/bin/java`.
- Runtime `PATH` includes both Java and mounted tool binaries:
  `/opt/java/openjdk/bin:/opt/nebula-tools:...`.
- Removed `mvn dependency:go-offline` from backend Dockerfiles to avoid long Docker builds.
- Frontend Dockerfile uses Docker BuildKit npm cache.
- WSL startup limits compose parallelism using `COMPOSE_PARALLEL_LIMIT=2`.
- Added `scripts/wsl/diagnose-runtime.sh` to verify Java and runtime tools inside containers.

## Start

```bash
./scripts/wsl/prepare-kubeconfig-for-docker.sh
./scripts/wsl/prepare-runtime-tools.sh
COMPOSE_PARALLEL_LIMIT=2 docker compose -p nebulaops-v22-2 -f docker-compose.yml up --build -d
./scripts/wsl/diagnose-runtime.sh
```

## Expected runtime check

Each Java service should show:

```text
openjdk version "21..."
```

If a tool is missing, endpoints return `live:false` with `toolStatus`; they do not return fake/static data.
