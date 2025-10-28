# NebulaOps v22.1 — SSO tools Prometheus health fix

This patch keeps Prometheus exposed on `http://localhost:9090` in both normal startup and `--with-sso-proxy` startup.

The OAuth2 Proxy profile remains dedicated to RabbitMQ Management, Mongo Express and Redis Commander. This avoids the health checker reporting Prometheus as down when only the SSO tool wrappers are enabled.

Expected tool behavior with `./scripts/wsl/start.sh --with-sso-proxy`:

- RabbitMQ: `http://localhost:15672` redirects to Keycloak/OAuth2 login.
- Mongo Express: `http://localhost:8088` redirects to Keycloak/OAuth2 login.
- Redis Commander: `http://localhost:8089` redirects to Keycloak/OAuth2 login.
- Prometheus: `http://localhost:9090/-/healthy` remains directly available for local health checks.
