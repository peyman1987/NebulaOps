# Deployment guide

## Local WSL/Docker start

```bash
cd nebulaops-v22.5
chmod +x scripts/wsl/*.sh scripts/*.sh
./scripts/wsl/start.sh --rebuild
./scripts/wsl/health.sh
```

Main URL:

```text
http://nebulaops.localhost
```

## Rebuild frontend only

Use this after changing the shell template, MFE remote bundles, Nginx config or static assets.

```bash
./scripts/wsl/build-frontend-local.sh
docker compose build frontend
docker compose up -d --force-recreate frontend
```

## Optional profiles

GitLab is optional:

```bash
./scripts/wsl/start.sh --with-gitlab --rebuild
```

SSO proxies for RabbitMQ, Mongo Express and Redis Commander are optional and should be enabled after the core stack is healthy:

```bash
./scripts/wsl/start.sh --with-sso-proxy --rebuild
```

## Browser routes

| Route | Purpose |
| --- | --- |
| `/` | Angular shell |
| `/remotes/<mfe>/` | standalone MFE page |
| `/api/**` | gateway API |
| `/keycloak/**` | Keycloak |
| `/grafana/**` | Grafana |
| `/prometheus/**` | Prometheus |
