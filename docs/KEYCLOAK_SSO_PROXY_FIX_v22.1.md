# NebulaOps v22.2 — Keycloak SSO Proxy Fix

This patch makes the local admin UIs behave consistently with the Keycloak-first authentication model.

## What changed

- Grafana remains configured with native Keycloak OIDC.
- Spring Boot services remain JWT resource servers.
- Angular frontend remains OIDC/PKCE.
- RabbitMQ Management, Prometheus, Mongo Express and Redis Commander are now exposed through OAuth2 Proxy.
- RabbitMQ, Mongo Express and Redis Commander also have an internal NGINX bridge that injects the local dev basic-auth credentials only after Keycloak has authenticated the browser session. This avoids showing the legacy RabbitMQ/Mongo/Redis login forms to the user.
- Direct public ports for the legacy admin UIs were removed. Public ports now belong to the SSO front door:
  - RabbitMQ Management: `http://localhost:15672`
  - Prometheus: `http://localhost:9090`
  - Mongo Express: `http://localhost:8088`
  - Redis Commander: `http://localhost:8089`
- The Keycloak login theme CSS was fixed so it does not hide the login page on Keycloak 24 parent-template render paths.

## Required one-time reset

Keycloak imports the realm only on a fresh database. Run this after applying the patch:

```bash
./scripts/keycloak-sso-reset.sh
```

Alternatively:

```bash
docker compose down --remove-orphans
docker volume rm nebulaops-v22-2_keycloak-db-data 2>/dev/null || true
./scripts/docker-network-fix.sh
docker compose up -d --build
```

## Test users

- `admin / admin`
- `peyman / admin`

## Notes

For production, replace the dev secrets and remove the basic-auth bridge pattern. Native OAuth2 should be configured per upstream product where supported. The bridge is intentionally local-development oriented and keeps the browser-facing authentication centralized on Keycloak.
