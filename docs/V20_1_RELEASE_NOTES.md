# NebulaOps v20.2 — Dynamic Enterprise Runtime

## Obiettivi

La versione v20.2 rimuove il feeling statico della v19.5 e porta più dati reali nel frontend.

## Novità principali

- Restyling UI Aurora Glass con card più leggibili, hover state, badge live e profondità visuale migliorata.
- Nuovo `PlatformLiveController` nel Gateway con endpoint `/api/platform/*`.
- Observability dinamica: health probe per Prometheus, Loki, Tempo, Grafana e OpenTelemetry Collector.
- Trace flow e latency heatmap generati da runtime telemetry invece di valori fissi.
- GitOps dinamico con lettura ArgoCD quando disponibile e fallback runtime-safe.
- DevSecOps dinamico con integrazione Trivy se installato e degradazione controllata se non disponibile.
- Docker Desktop tab corretto: normalizzazione del formato reale di `docker ps`, `docker images`, `docker volume ls`.
- Ambienti dinamici via `/api/platform/environments`.

## Endpoint aggiunti

- `GET /api/platform/observability`
- `GET /api/platform/gitops`
- `GET /api/platform/devsecops`
- `GET /api/platform/environments`

## Note

Gli endpoint usano dati reali quando i tool sono disponibili nel container/host (`docker`, `kubectl`, `argocd`, `trivy`,
servizi observability). In assenza dei tool, il backend restituisce fallback runtime marcati come non raggiungibili o
degradati, evitando crash UI.
