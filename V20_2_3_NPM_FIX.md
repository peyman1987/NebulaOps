# NebulaOps v20.2.3 - NPM/Docker Build Fix

This release fixes the frontend Docker build failure caused by an out-of-sync `package-lock.json` and Angular 18 Node
engine requirements.

## Changes

- Removed stale `frontend/package-lock.json` from the distribution.
- Updated `frontend/Dockerfile` to use `node:20.21.1`.
- Replaced `npm ci` with `npm install --include=dev --legacy-peer-deps`.
- Dockerfile now copies only `package.json` before install, preventing stale lockfiles from breaking the Docker layer.
- Added `frontend/.npmrc` with non-strict engine handling and legacy peer dependency mode.

## Clean rebuild command

```bash
docker compose down --remove-orphans
docker builder prune -f
docker compose build --no-cache frontend
docker compose up -d
```

If you build all services:

```bash
docker compose build --no-cache
docker compose up -d
```
