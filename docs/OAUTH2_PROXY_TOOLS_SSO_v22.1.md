# NebulaOps v22.2 — OAuth2 Proxy SSO for Tool UIs

This package protects the local tool UIs with Keycloak when the `sso-proxy` Docker Compose profile is enabled.

## Protected URLs

| Tool | URL | Authentication mode |
|---|---:|---|
| RabbitMQ Management | `http://localhost:15672` | Keycloak via OAuth2 Proxy |
| Mongo Express | `http://localhost:8088` | Keycloak via OAuth2 Proxy |
| Redis Commander | `http://localhost:8089` | Keycloak via OAuth2 Proxy |
| Grafana | `http://localhost:3000` | Native Keycloak OIDC |

The native basic-auth UIs are still available only inside the Docker network. The browser-facing ports are owned by OAuth2 Proxy when `--with-sso-proxy` is used.

## Start

```bash
./scripts/wsl/start.sh --with-sso-proxy
```

or:

```bash
./scripts/wsl/start-sso-tools.sh
```

## Repair old Keycloak volumes

If Keycloak shows `Client not found`, run:

```bash
./scripts/keycloak-ensure-sso-clients.sh
```

For a clean reimport of the realm and theme:

```bash
./scripts/keycloak-sso-reset.sh
```

## Validate

```bash
./scripts/wsl/health.sh
./scripts/wsl/diagnose-sso.sh
```

Expected unauthenticated checks for RabbitMQ, Mongo Express and Redis Commander are HTTP redirects to `/oauth2/start` or to the Keycloak login page. That is a healthy SSO-protected state.

## Notes

OAuth2 Proxy uses explicit Keycloak endpoints so that browser traffic can use `localhost:8180`, while containers can still reach Keycloak internally through `keycloak:8080`. The script `keycloak-ensure-sso-clients.sh` guarantees that the clients `grafana` and `nebulaops-oauth2-proxy` exist even when an older Keycloak database volume is reused.
