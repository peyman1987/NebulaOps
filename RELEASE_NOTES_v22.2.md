# NebulaOps v22.2 — True Micro Frontends

Questa release corregge la prima iterazione v22.2: la UI non è più una singola SPA con sezioni logiche, ma una shell host con tre micro frontend realmente separati e deployabili autonomamente.

## Frontend architecture

- `frontend/` — Angular shell host, porta `4200`.
- `frontend/remotes/docker-openlens` — Docker Desktop + OpenLens remote, porta `4211`.
- `frontend/remotes/task-management` — Task Management remote, porta `4212`.
- `frontend/remotes/platform-core` — Platform Core remote, porta `4213`.

Le remote app espongono `remoteEntry.js`, registrano custom elements e possono girare standalone. La shell carica dinamicamente i remote entry e monta il relativo custom element.

## UI changes

- App bar trasformata in modal moderno con ricerca.
- Header con badge versione v22.2 in effetto 3D.
- Favicon SVG aggiunta e collegata in `index.html`.
- Layout shell/remotes più pulito, responsive e con pannelli glass/3D.

## Runtime

`docker compose up -d --build` avvia shell e tre remote come container distinti.


## v22.2 portfix — SSO/native tool UI port isolation

This patch fixes startup failures when switching between native tool UIs and the OAuth2 Proxy SSO mode. The WSL start script now checks ports `15672`, `8088`, and `8089` before Compose starts and safely removes stale NebulaOps containers from previous versions that still own those ports. Non-NebulaOps containers are not removed automatically; the script prints an explicit diagnostic instead.

Use:

```bash
./scripts/wsl/fix-tool-ui-ports.sh
./scripts/wsl/start.sh --with-sso-proxy
```

## v22.2 expanded micro frontend side-menu patch

- Split del precedente Platform Core in micro frontend dedicati: Docker Desktop, OpenLens Kubernetes, Observability, CI/CD + GitOps, Terraform Studio, DevSecOps, AI Ops e FinOps Cost.
- Task Management resta remote indipendente con creazione task protetta da Keycloak.
- La shell host mantiene il menu laterale persistente: i remote non duplicano sidebar, sessione o app launcher.
- Ogni remote espone `remoteEntry.js`, `manifest.json`, `Dockerfile`, `nginx.conf` e porta dedicata 4211-4219.
- `health.sh` verifica tutti i remote entry.


## Verified startup hardening

- Added cwd-independent remote verification with `frontend/tools/verify-remotes.mjs`.
- Added healthchecks for the shell host and all nine Nginx-served micro frontend remotes.
- Added healthchecks for OAuth2 Proxy wrappers.
- Added `scripts/wsl/preflight-v22.2.sh` for repeatable static validation before starting the stack.

## v22.2 shell usability patch

- Restored the `INFRA` section in the sidebar and moved Kubernetes/Terraform navigation under it.
- Replaced the decorative sidebar search with a working MFE filter by name, scope, group and port.
- Added a shell-level runtime dashboard inside the main frame so the page is informative even before a remote renders data.
- Added `@nebulaops/mfe-core` to centralize Angular Element bootstrap and JWT propagation across remotes.
- Added `nebulaops-shared-kernel` to centralize backend contracts/constants for micro backend services.

## v22.2 Docker MFE shared-library build fix

- Fixed isolated Docker builds for all Angular remotes using `@nebulaops/mfe-core`.
- Remote Dockerfiles now copy the shared MFE library into each remote build workspace before `ng build`.
- TypeScript path resolution now checks the local Docker workspace first and falls back to the central `frontend/libs` source for local development.
- This prevents `Cannot find module '@angular/core'`, `@angular/common/http`, `@angular/elements` and related Angular package resolution errors when a remote is built as an independent image.
