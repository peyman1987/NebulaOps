# NebulaOps v21.4 — Keycloak Custom Login

This build keeps the NebulaOps customized Keycloak login enabled.

## What is different

- `loginTheme` remains `nebulaops`.
- `login.ftl` is standalone and does not import `template.ftl`.
- `template.ftl` is intentionally not shipped in the custom theme to avoid FreeMarker import crashes.
- `start.sh` checks the OIDC login endpoint with a PKCE request.
- If Keycloak returns HTTP 500, `start.sh` rewrites the custom standalone login and restarts Keycloak.
- It does **not** switch the realm to the default Keycloak login theme.

## Run

```bash
cd /mnt/d/workspace/personal/portfolio/nebulaops-v21.4
docker compose -p nebulaops-v21-4 down -v
./scripts/wsl/start.sh
./scripts/wsl/health.sh
```

## Expected health result

```text
Keycloak custom login   HTTP 200 login page
```

## Debug

```bash
docker compose -p nebulaops-v21-4 logs keycloak --tail=160 | grep -iE 'freemarker|template|ERROR'
```
