# Start here — NebulaOps v22.2

## Run it

```bash
./scripts/wsl/start.sh
```

Wait ~60s for all 25 containers. Then open **http://localhost:4200**.

## If something's off

```bash
./scripts/wsl/health.sh           # green/red status of every endpoint
./scripts/wsl/logs.sh <service>   # tail any service
./scripts/wsl/gateway-logs.sh     # focused gateway diagnostic
```

## Common cases

**Gateway returns 502 on all endpoints**
Cached image. Force rebuild:
```bash
./scripts/wsl/start.sh --rebuild-gateway
```

**No Kubernetes cluster available**
Live K8s endpoints return `live:false` — UI shows empty state. Everything
else (tasks, auth, observability) keeps working.

**Want to wipe state and start fresh**
```bash
./scripts/wsl/stop.sh -v
./scripts/wsl/docker-cache-repair.sh   # last-resort cache wipe
./scripts/wsl/start.sh
```

## Read next

- `README.md` — full overview, project layout, all URLs
- `ARCHITECTURE.md` — component diagram and data flow
- `RELEASE_NOTES_v22.2.md` — what changed in this release
- `config/platform.yml` — every service URL in one place

## v22.2 Keycloak SSO proxy patch

- RabbitMQ Management, Prometheus, Mongo Express and Redis Commander are now behind Keycloak via OAuth2 Proxy.
- The RabbitMQ/Mongo/Redis legacy login forms are bypassed through an internal dev-only NGINX bridge after Keycloak authentication.
- The Keycloak login theme has been fixed to avoid blank login pages with Keycloak 24.
- Run `./scripts/keycloak-sso-reset.sh` after upgrading so the realm is reimported.
