# NebulaOps v24.1 — Angular Micro Frontends

Each remote is a standalone Angular 18 project compiled with `@angular/elements` to expose a Web Component registered in the shell host.

## Structure of each remote

```
remotes/<name>/
  src/
    main.ts               # Bootstrap Angular Elements + customElements.define
    index.html            # Entry point standalone (for dev)
    styles.css            # Global styles
    app/
      app.component.ts    # Component Angular standalone
      app.component.html  # Angular template with @for, @if (new control flow)
      app.component.css   # Component styles
  angular.json            # Angular CLI config
  tsconfig.json           # TypeScript config
  tsconfig.app.json       # App-specific TS config
  package.json            # Dependencies (Angular 18 + @angular/elements)
  Dockerfile              # Multi-stage: node:20-alpine build + nginx:1.27-alpine serve
  nginx.conf              # nginx with proxy → gateway-service:8080
```

## Local development

```bash
cd frontend/remotes/<name>
npm install --legacy-peer-deps
npm start   # ng serve --host 0.0.0.0 --port <port>
```

## Production build

```bash
npm run build
# Output: dist/browser/
```

## Docker execution

```bash
docker build -t nebulaops-mfe-<name>:24.1 .
docker run -p <port>:80 nebulaops-mfe-<name>:24.1
```

## How it works with the Angular shell

The host shell in `frontend/src/app/app.component.html` uses custom tags such as:
```html
<nebulaops-mfe-openlens-kubernetes></nebulaops-mfe-openlens-kubernetes>
```

These tags are loaded dynamically by the shell through `loadRemote()`, which injects the remote `remoteEntry.js` script.

## Available remotes

| Remote               | Tag custom element                    | Port | Scope                   |
|----------------------|---------------------------------------|------|-------------------------|
| platform-catalog     | nebulaops-mfe-platform-catalog        | 4220 | Platform · Catalog     |
| docker-desktop       | nebulaops-mfe-docker-desktop          | 4211 | Runtime · Docker        |
| openlens-kubernetes  | nebulaops-mfe-openlens-kubernetes     | 4212 | Runtime · Kubernetes    |
| task-management      | nebulaops-mfe-task-management         | 4213 | Delivery · Tasks        |
| observability        | nebulaops-mfe-observability           | 4214 | SRE · Metrics/Logs      |
| cicd-gitops          | nebulaops-mfe-cicd-gitops             | 4215 | DevOps · Pipelines      |
| terraform-studio     | nebulaops-mfe-terraform-studio        | 4216 | IaC · Environments      |
| devsecops            | nebulaops-mfe-devsecops               | 4217 | Security · Vulns        |
| ai-ops               | nebulaops-mfe-ai-ops                  | 4218 | AI · RCA/Assist         |
| finops-cost          | nebulaops-mfe-finops-cost             | 4219 | Cost · Analytics        |

## Technical notes

- **Angular 18** con `@angular/elements` per Web Component
- **Standalone Components** — no NgModule
- **New Control Flow** — `@for`, `@if`, `@let` (Angular 17+)
- **Signals** — `signal()`, `computed()` for state management
- **Shared JWT** — `localStorage.getItem('nebulaops.v24_1.jwt')` read by each MFE
- **CORS** — nginx returns `Access-Control-Allow-Origin: *` for cross-origin embedding
