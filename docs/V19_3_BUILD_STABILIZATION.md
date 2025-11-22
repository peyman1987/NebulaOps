# NebulaOps v19.3 — Build Stabilization Patch

This corrected v19.3 package includes the DevSecOps module plus hardening fixes for Docker and Maven builds.

## Fixed

- Docker Compose project name set to `nebulaops-v19-3`.
- Explicit image names added for all local services, replacing accidental `nebulaops-v22-2-*` output names.
- All Spring Boot Dockerfiles now use BuildKit Maven cache mounts.
- Maven build commands now use batch mode, retry settings, longer HTTP TTL, and `-Dmaven.test.skip=true`.
- Backend Maven artifact versions aligned to `19.3.0`.
- Documentation, Markdown references, and SVG labels aligned to v19.3.

## Recommended build command

```bash
DOCKER_BUILDKIT=1 docker compose build --parallel=false
docker compose up
```

For faster local rebuilds after the first dependency cache warm-up:

```bash
DOCKER_BUILDKIT=1 docker compose build
docker compose up
```

## Why this patch exists

The previous failure was caused by a transient Maven Central timeout while resolving `org.springframework:spring-test`.
The frontend build completed successfully; the failure happened in `auth-service` during Maven dependency resolution.
