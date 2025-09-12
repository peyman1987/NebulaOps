# NebulaOps v21.4 — Clean Package

Questo pacchetto contiene direttamente il tema Keycloak corretto e non richiede hotfix separati.

## Avvio WSL

```bash
cd /mnt/d/workspace/personal/portfolio/nebulaops-v21.4
./scripts/wsl/start.sh
./scripts/wsl/health.sh
```

## Login

- Frontend: http://localhost:4200
- Keycloak: http://localhost:8180
- Realm: `nebulaops`
- Client: `nebulaops-frontend`
- PKCE: `S256`

Il login theme `nebulaops` è standalone: `login.ftl` non importa più `template.ftl`, così evita l'errore FreeMarker che causava HTTP 500.
