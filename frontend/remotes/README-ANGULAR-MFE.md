# NebulaOps v22.5 — Angular Micro Frontends

Ogni remote è un progetto Angular 18 standalone, compilato con `@angular/elements`
per esporre un Web Component registrato nella shell host.

## Struttura di ogni remote

```
remotes/<name>/
  src/
    main.ts               # Bootstrap Angular Elements + customElements.define
    index.html            # Entry point standalone (for dev)
    styles.css            # Global styles
    app/
      app.component.ts    # Component Angular standalone
      app.component.html  # Template Angular con @for, @if (nuovo control flow)
      app.component.css   # Component styles
  angular.json            # Angular CLI config
  tsconfig.json           # TypeScript config
  tsconfig.app.json       # App-specific TS config
  package.json            # Dependencies (Angular 18 + @angular/elements)
  Dockerfile              # Multi-stage: node:20-alpine build + nginx:1.27-alpine serve
  nginx.conf              # nginx con proxy → gateway-service:8080
```

## Come sviluppare in locale

```bash
cd frontend/remotes/<name>
npm install --legacy-peer-deps
npm start   # ng serve --host 0.0.0.0 --port <port>
```

## Come fare il build production

```bash
npm run build
# Output: dist/browser/
```

## Come eseguire con Docker

```bash
docker build -t nebulaops-mfe-<name>:22.5 .
docker run -p <port>:80 nebulaops-mfe-<name>:22.5
```

## Come funziona con la Shell Angular

La shell host in `frontend/src/app/app.component.html` usa tag custom come:
```html
<nebulaops-mfe-openlens-kubernetes></nebulaops-mfe-openlens-kubernetes>
```

Questi tag vengono caricati dinamicamente dalla shell tramite `loadRemote()` che
inietta lo script `remoteEntry.js` (output Angular build) dal remote.

## Remotes disponibili

| Remote               | Tag custom element                    | Port | Scope                   |
|----------------------|---------------------------------------|------|-------------------------|
| docker-desktop       | nebulaops-mfe-docker-desktop          | 4211 | Runtime · Docker        |
| openlens-kubernetes  | nebulaops-mfe-openlens-kubernetes     | 4212 | Runtime · Kubernetes    |
| task-management      | nebulaops-mfe-task-management         | 4213 | Delivery · Tasks        |
| observability        | nebulaops-mfe-observability           | 4214 | SRE · Metrics/Logs      |
| cicd-gitops          | nebulaops-mfe-cicd-gitops             | 4215 | DevOps · Pipelines      |
| terraform-studio     | nebulaops-mfe-terraform-studio        | 4216 | IaC · Environments      |
| devsecops            | nebulaops-mfe-devsecops               | 4217 | Security · Vulns        |
| ai-ops               | nebulaops-mfe-ai-ops                  | 4218 | AI · RCA/Assist         |
| finops-cost          | nebulaops-mfe-finops-cost             | 4219 | Cost · Analytics        |

## Note tecniche

- **Angular 18** con `@angular/elements` per Web Component
- **Standalone Components** — nessun NgModule
- **Nuovo Control Flow** — `@for`, `@if`, `@let` (Angular 17+)
- **Signals** — `signal()`, `computed()` per state management
- **JWT condiviso** — `localStorage.getItem('nebulaops.v22_5.jwt')` letto da ogni MFE
- **CORS** — nginx restituisce `Access-Control-Allow-Origin: *` per cross-origin embedding
