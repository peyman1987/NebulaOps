# NebulaOps v22.1 — Keycloak Platform Release

## Release focus

NebulaOps v22.1 promotes the local DevOps cockpit into a fuller platform release with a modernized Angular header and Keycloak-based authentication propagated across the Spring microservices. GitLab CE remains available only as an optional Docker Compose profile because it is heavy in local Docker/WSL environments.

## Highlights

- Updated active project identity to **v22.1 / 22.1.0** across frontend metadata, Maven parent/module versions, Docker Compose project names, environment examples, scripts, health endpoints and operational documentation.
- Refreshed the Angular header with a modern service dock and quick-access buttons for Keycloak, GitLab, Grafana, Prometheus, RabbitMQ, Mongo Express, Redis Commander, Gateway, CI/CD, Kubernetes and AI Ops.
- Kept **GitLab CE** as an optional Docker Compose profile, exposed at `http://localhost:8929` only when started with `--with-gitlab`.
- Added GitLab to the frontend Home launcher, INFRA catalog and endpoint matrix.
- Integrated GitLab with Keycloak through the NebulaOps realm using an OpenID Connect client named `gitlab`.
- Added Keycloak resource-server support to all Spring Boot services using JWT validation through the NebulaOps realm JWKS endpoint.
- Updated the gateway RestTemplate to relay incoming Bearer tokens to downstream services.
- Added `cost-analytics-service` to the backend Maven reactor and CI build matrix.
- Added v22.1 frontend SVG assets so all dashboard image references resolve to the new release path.
- Extended CI/CD build jobs to include all backend services, Go workers and the AI engine.

## Local endpoints

| Service | URL | Note |
| --- | --- | --- |
| Frontend | `http://localhost:4200` | Angular cockpit |
| Gateway | `http://localhost:8080` | API entrypoint |
| Keycloak | `http://localhost:8180` | Realm `nebulaops` |
| GitLab | optional: `./scripts/wsl/start.sh --with-gitlab` | Disabled by default because it is heavy |
| Grafana | `http://localhost:3000` | Observability UI |
| Prometheus | `http://localhost:9090` | Metrics |
| RabbitMQ | `http://localhost:15672` | Queue management |
| Mongo Express | `http://localhost:8088` | Mongo UI |
| Redis Commander | `http://localhost:8089` | Redis UI |

## Validation performed in this package

- YAML validation completed successfully.
- Shell script syntax validation completed successfully.
- Package validator completed successfully.
- Go unit tests for `go/cache-service` and `go/event-worker` completed successfully.
- TypeScript parser smoke check completed after v22.1 edits; only missing dependency diagnostics are expected in this sandbox because `node_modules` is not present.

## Notes

The execution environment used to prepare this archive does not include Maven or Docker, and `npm install` did not complete within the sandbox timeout. Full backend Maven tests, Docker Compose startup and Angular production build should be executed on the target workstation/CI runner with internet/package-cache access.

## v22.1 Keycloak full-auth patch

- Added Keycloak role mapping for every Spring Boot resource server.
- Added JWT/JWKS protection to the Go cache service HTTP API.
- Added JWT/JWKS protection to the FastAPI AI engine.
- Added Grafana Generic OAuth client and Docker Compose environment.
- Added `nebulaops-api` and `nebulaops-oauth2-proxy` clients to the Keycloak realm.
- Added `scripts/keycloak-smoke-test.sh` and `scripts/keycloak-reimport-realm.sh`.

## v22.1 Keycloak Lite runtime

GitLab CE is now optional because it is heavy in local Docker/WSL environments. The default stack starts Keycloak, Grafana, Prometheus, RabbitMQ, Mongo/Redis and all NebulaOps services, but does not start GitLab. To enable GitLab explicitly:

```bash
./scripts/wsl/start.sh --with-gitlab
# or
COMPOSE_PROFILES=gitlab docker compose -p nebulaops-v22-1 up -d gitlab
```

To remove a running GitLab container without touching Keycloak authentication:

```bash
./scripts/wsl/disable-gitlab.sh
```

## v22.1 Keycloak SSO proxy patch

- RabbitMQ Management, Prometheus, Mongo Express and Redis Commander are now behind Keycloak via OAuth2 Proxy.
- The RabbitMQ/Mongo/Redis legacy login forms are bypassed through an internal dev-only NGINX bridge after Keycloak authentication.
- The Keycloak login theme has been fixed to avoid blank login pages with Keycloak 24.
- Run `./scripts/keycloak-sso-reset.sh` after upgrading so the realm is reimported.

## Patch — OAuth2 Proxy per tool UI

- Aggiunto profilo `sso-proxy` per proteggere RabbitMQ Management, Mongo Express e Redis Commander tramite Keycloak.
- Aggiunti reverse proxy interni Nginx per collegare OAuth2 Proxy alle UI legacy con Basic Auth interna.
- Aggiunto `scripts/wsl/start-sso-tools.sh` per avvio rapido delle UI protette.
- Aggiunto `scripts/keycloak-ensure-sso-clients.sh` per creare/aggiornare i client Keycloak anche con un volume Keycloak già esistente.
- Aggiunto `scripts/oauth2-proxy-preflight.sh` per diagnosticare pull image/DNS prima dello start.

## v22.1 SSO verified patch

- Strengthened Keycloak client self-healing for stale Keycloak database volumes.
- Added Keycloak-aware health checks for protected gateway API routes.
- Added OAuth2 Proxy redirect validation for RabbitMQ, Mongo Express and Redis Commander.
- Added `scripts/wsl/diagnose-sso.sh` for targeted SSO troubleshooting.
- Updated smoke tests to call the gateway with a real Keycloak Bearer token.
- Marked optional GitLab as skipped in health output when the profile is disabled.
