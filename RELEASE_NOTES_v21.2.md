# NebulaOps v21.2 — Release Notes

## Highlights

This release focuses on **operational maturity**: centralized configuration,
modernized UI, hardened backend communication, and ergonomic developer scripts.

## What's new

### 1. Centralized configuration (single source of truth)

The previous releases scattered service URLs across `docker-compose.yml`,
`application.yml`, Angular HTTP calls, and shell scripts. All of these are
now derived from a single registry:

- **`config/platform.yml`** — service hostnames, ports, API routes, version metadata
- **`frontend/src/app/api.config.ts`** — typed wrapper consumed by the Angular app
- Gateway `application.yml` and `docker-compose.yml` environment blocks reference
  the same canonical names

When a service URL or port changes, edit `config/platform.yml` and the relevant
consumer files in one place. No more hunting through 10 files.

### 2. Frontend visual polish

Layered on top of the existing v21.1 aurora theme without breaking any layout:

- **Design tokens** (`frontend/src/styles.css`) — CSS custom properties for
  colors, spacing, radii, shadows, motion. All components now derive from these.
- Refined typography (gradient hero text, sharper hierarchy)
- Smoother panel/card hover transitions
- Custom scrollbar styling matching the dark theme
- Connection status pills (live/offline) ready to wire into any component
- Toast notification slot
- Empty-state component
- Loading skeleton with shimmer animation
- Improved focus states for keyboard navigation
- `prefers-reduced-motion` support

### 3. Backend — robust gateway proxy

`ProxyController` now:
- Uses a clean `Call` functional interface for forwarding
- Returns sensible fallback bodies when downstream services are unreachable
  (empty lists, structured error maps) instead of 500/502
- Logs the actual auth/task/ai-ops downstream URL from `proxy.*` properties,
  fed from `docker-compose.yml` environment variables, fed from `config/platform.yml`

`HealthController` exposes `/api/health` with version 21.2 metadata.

All existing live endpoints (Kubernetes, Docker, Helm, Observability, GitOps,
DevSecOps, Environments) preserved with the same Angular-compatible shapes from v21.1.

### 4. Optimized WSL scripts

- `start.sh` now uses a single optimized build pass, with optional `--rebuild-gateway`
  flag for when only the gateway needs no-cache rebuilding
- `restart-gateway.sh` — one-shot fast restart of just the gateway
- `health.sh` — checks all 25+ services with traffic-light status
- All scripts source `lib/common.sh` for shared functions (logging, status colors)

### 5. Updated docs and diagrams

- New `ARCHITECTURE.md` with current service topology
- All `nebulaops-v21-2-*.svg` diagrams refreshed (10 architecture diagrams)
- Updated `docs/diagrams/*.svg` to reflect v21.2 topology
- `README.md` rewritten with new quickstart, troubleshooting, config reference

## Upgrade from v21.1

```bash
cd nebulaops-v21.2
./scripts/wsl/start.sh
```

If the gateway misbehaves after upgrade (rare — caused by stale BuildKit cache):
```bash
./scripts/wsl/start.sh --rebuild-gateway
```

## Compatibility

- Java 21, Node 22, Docker Compose v2
- WSL2 on Windows or native Linux
- `kubectl`, `docker`, `helm` required (mounted automatically)
- `trivy`, `terraform`, `argocd` optional (endpoints return `live:false` if missing)
