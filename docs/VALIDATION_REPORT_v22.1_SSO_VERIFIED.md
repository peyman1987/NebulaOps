# NebulaOps v22.2 — SSO Verified Package Validation

## Scope

This validation pass focuses on the Keycloak/OAuth2 Proxy integration for:

- Grafana native OIDC
- RabbitMQ Management behind OAuth2 Proxy
- Mongo Express behind OAuth2 Proxy
- Redis Commander behind OAuth2 Proxy
- Gateway smoke checks with a real Keycloak Bearer token

## Fixes applied

- `scripts/keycloak-ensure-sso-clients.sh` now creates/updates all required clients from the realm file, not only Grafana and OAuth2 Proxy.
- `scripts/wsl/start.sh` now waits on `gateway-service` through `/actuator/health`, not a protected `/api` endpoint.
- `scripts/wsl/start.sh` validates SSO redirect behavior when `--with-sso-proxy` is enabled.
- `scripts/wsl/health.sh` is Keycloak-aware and uses a token for gateway API checks.
- `scripts/wsl/health.sh` treats disabled GitLab as skipped, not failed.
- `scripts/wsl/health.sh` detects whether RabbitMQ, Mongo Express and Redis Commander are native or SSO-protected.
- OAuth2 Proxy containers now use explicit OIDC endpoints with discovery disabled for stable Docker/WSL localhost behavior.
- Added `scripts/wsl/diagnose-sso.sh` for focused SSO diagnostics.

## Static validation performed

- YAML parse validation for Docker Compose files.
- Keycloak realm JSON parse validation.
- Bash syntax validation for all scripts.
- Go tests for the Go modules when available in the environment.
- Python syntax validation for the FastAPI service.
- Frontend TypeScript config/package smoke validation.
- ZIP integrity validation.

## Runtime validation note

Docker and Maven are not available in this artifact generation environment, so live `docker compose up` and `mvn test` cannot be executed here. The package includes runtime validation scripts to run locally:

```bash
./scripts/wsl/start.sh --with-sso-proxy
./scripts/wsl/health.sh
./scripts/wsl/diagnose-sso.sh
./scripts/wsl/smoke-test.sh
```
