# NebulaOps v20.2 Full Platform

v20.2 transforms the cockpit into a full platform UI with a persistent left sidebar and dedicated pages for every major
operational domain.

## New pages

- Container Registry: image inventory, tags, SBOM/promotion gates.
- Service Mesh: traffic split, mTLS, retries and service map.
- Secrets Vault: secret inventory, expiry and rotation center.
- Database Ops: connection, query, backup and restore status.
- Queues & Streams: RabbitMQ/Kafka style lag, consumers, DLQ and event stream.
- SLO Center: availability, latency, error budget and burn-rate alerts.
- Incident Room: active incidents, timeline and runbook.
- Audit Trail: operator actions, deployment evidence and compliance exports.

## Navigation

The dashboard remains the main page, but the UI now includes a left sidebar grouped by Command, Runtime, Delivery,
Reliability, Security, and Data & Cost.

## Validation

Frontend production build validated with `npm run build`.
