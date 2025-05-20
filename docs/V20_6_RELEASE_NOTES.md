# NebulaOps v21.2 Corrected Release Notes

## Backend

- Controller → Service → Client/Adapter → real Tool/API/CLI model restored.
- Removed build-time downloads of enterprise CLIs from Java service Dockerfiles.
- Shell execution uses `sh -lc` for portable JRE images.
- OpenAPI 3 via springdoc added to backend services.
- Static OpenAPI YAML references added under `docs/openapi`.

## Runtime

Real runtime tools are expected from:

- host mounts
- custom base images
- sidecars
- existing cluster environment

No fake/statistical backend values are produced to hide missing tools.
