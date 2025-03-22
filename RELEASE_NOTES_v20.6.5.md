# NebulaOps v20.6.5 Corrected Live Platform

This release was regenerated from the provided v20.6 package with a focused fix for the repeated runtime and build
failures.

## Verified before packaging

- No backend Dockerfile contains `dependency:go-offline`.
- Every Spring Boot backend runtime image is `eclipse-temurin:21-jre`.
- Every Spring Boot backend entrypoint uses `/opt/java/openjdk/bin/java` directly.
- Docker Compose `PATH` keeps Java first and adds `/opt/nebula-tools` for mounted CLIs.
- Shell scripts pass `bash -n` syntax validation.
- `docker-compose.yml` passes YAML validation.
- Local `.kube` and `.runtime-tools` folders are excluded from the release package.

## Runtime tools

The backend still returns real data only. Required CLIs must be present on the host and mounted with:

```bash
./scripts/wsl/prepare-kubeconfig-for-docker.sh
./scripts/wsl/prepare-runtime-tools.sh
```

Then start:

```bash
COMPOSE_PARALLEL_LIMIT=2 docker compose -p nebulaops-v20-6 -f docker-compose.yml up --build -d
./scripts/wsl/diagnose-runtime.sh
```

If `kubectl`, `docker`, `helm`, `trivy`, `terraform`, or `argocd` are missing, the corresponding API
returns `live:false` with `toolStatus`; it does not return mock data.
