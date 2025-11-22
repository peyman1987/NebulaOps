# NebulaOps v22.2 — Angular Micro Frontends

Each remote is a standalone Angular 18 project compiled with `@angular/elements` to expose a Web Component registered by the host shell.

## Remote structure

```text
remotes/<name>/
  src/
    main.ts               # Angular Elements bootstrap + customElements.define
    index.html            # Standalone entry point for development
    styles.css            # Global styles
    app/
      app.component.ts    # Standalone Angular component
      app.component.html  # Angular template using @for and @if control flow
      app.component.css   # Component styles
  angular.json            # Angular CLI config
  tsconfig.json           # TypeScript config
  tsconfig.app.json       # App-specific TS config
  package.json            # Dependencies (Angular 18 + @angular/elements)
  Dockerfile              # Multi-stage build: node:20-alpine + nginx:1.27-alpine
  nginx.conf              # nginx with proxy to gateway-service:8080
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
docker build -t nebulaops-mfe-<name>:22.2 .
docker run -p <port>:80 nebulaops-mfe-<name>:22.2
```

## Shell integration

The host shell in `frontend/src/app/app.component.html` uses custom tags such as:

```html
<nebulaops-mfe-openlens-kubernetes></nebulaops-mfe-openlens-kubernetes>
```

The shell loads those tags dynamically through `loadRemote()`, injecting the `remoteEntry.js` script produced by each remote build.

## Available remotes

| Remote               | Custom element tag                     | Port | Scope                   |
|----------------------|----------------------------------------|------|-------------------------|
| docker-desktop       | nebulaops-mfe-docker-desktop           | 4211 | Runtime · Docker        |
| infra-hub            | nebulaops-mfe-infra-hub                | 4220 | Infra · Console Hub     |
| openlens-kubernetes  | nebulaops-mfe-openlens-kubernetes      | 4212 | Runtime · Kubernetes    |
| task-management      | nebulaops-mfe-task-management          | 4213 | Delivery · Tasks        |
| observability        | nebulaops-mfe-observability            | 4214 | SRE · Metrics/Logs      |
| cicd-gitops          | nebulaops-mfe-cicd-gitops              | 4215 | DevOps · Pipelines      |
| terraform-studio     | nebulaops-mfe-terraform-studio         | 4216 | IaC · Environments      |
| devsecops            | nebulaops-mfe-devsecops                | 4217 | Security · Vulns        |
| ai-ops               | nebulaops-mfe-ai-ops                   | 4218 | AI · RCA/Assist         |
| finops-cost          | nebulaops-mfe-finops-cost              | 4219 | Cost · Analytics        |

## Technical notes

- **Angular 18** with `@angular/elements` for Web Components.
- **Standalone Components** with no NgModule dependency.
- **Modern control flow** with `@for`, `@if` and `@let`.
- **Signals** through `signal()` and `computed()` for state management.
- **Shared JWT** via `localStorage.getItem('nebulaops.v22_2.jwt')`, read by every MFE.
- **CORS** configured by nginx with `Access-Control-Allow-Origin: *` for cross-origin embedding.

`infra-hub` restores the previous INFRA console hub as a dedicated remote application on port `4220`.
