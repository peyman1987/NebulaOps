# NebulaOps v19.3 - Home Feature Launcher

La versione corretta di NebulaOps v19.3 aggiunge dalla pagina Home un **Command Center** con tasti grandi e cliccabili
per accedere direttamente alle feature principali.

## Collegamenti esterni

| Feature    |            URL locale | Uso                        |
|------------|----------------------:|----------------------------|
| Grafana    | http://localhost:3000 | Dashboard, logs e metriche |
| ArgoCD     | http://localhost:8080 | GitOps sync e applicazioni |
| Prometheus | http://localhost:9090 | Query metriche e target    |

## Collegamenti interni

| Feature                   | Tab             |
|---------------------------|-----------------|
| AI OPS                    | `AI OPS`        |
| Kubernetes Visual Cluster | `KUBERNETES`    |
| DevSecOps Security        | `SECURITY`      |
| Helm                      | `HELM`          |
| Observability             | `OBSERVABILITY` |

## UI

I launcher sono card glassmorphism con icone grandi, colori coerenti con i prodotti e hover glow.

## File aggiornati

- `frontend/src/app/app.component.html`
- `frontend/src/app/app.component.ts`
- `frontend/src/app/app.component.css`
- `README.md`
- `PROJECT_METADATA.md`
- `docs/README_V19_3_INDEX.md`
- `docs/V19_3_RELEASE_NOTES.md`
