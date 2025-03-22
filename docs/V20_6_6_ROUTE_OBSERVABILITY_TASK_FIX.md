# NebulaOps v20.6.6 Route, Observability and Task Fix

Fix applicati:

- Aggiunta compatibilità `/api/ai-ops/**` e `/api/aiops/**` nel servizio AI Ops.
- Aggiunti endpoint reali `POST /api/ai-ops/analyze` e `POST /api/ai-ops/autofix`.
- `ObservabilityPlatformService` non deve più generare HTTP 500 se Prometheus/Loki/Tempo/Grafana non sono pronti:
  ritorna sempre `live:false` per il probe non raggiungibile.
- Lo schema `/api/platform/observability` espone sia `stack` sia `items`, così la UI lo legge correttamente.
- Reintrodotta voce `Tasks` nel menu laterale Platform.

Questa fix non introduce dati statici: quando un tool/API non risponde, il backend ritorna stato live falso e dettaglio
errore.
