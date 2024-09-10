# NebulaOps v19.3 — Kubernetes Visual Cluster

Versione: **19.3**

## Obiettivo

La v19.3 aggiunge una console Kubernetes visuale in stile Lens, K9s, Datadog topology e Portainer futuristic. La vista
KUBERNETES non è più solo CRUD: diventa una mappa operativa 3D con topology live, drilldown pod e telemetria animata.

## Frontend

### Cluster topology 3D live

La dashboard mostra:

- ingress
- frontend pods
- gateway-service
- task-service
- notification-service
- RabbitMQ
- MongoDB
- persistent volumes

### Effetti visuali

- cyber grid Kubernetes blue
- glassmorphism cards
- connessioni animate tra componenti
- particelle di traffico realtime
- barre CPU/RAM animate
- nodo rosso pulsante in caso di crash/restart
- simulazione pod restart

## Interazioni

Click su un pod o nodo apre il drilldown operativo con:

- logs live
- terminal embedded simulato `kubectl describe`
- metrics CPU/RAM/network/restart risk
- events live
- YAML live editor

## Integrazione con AI Ops

La vista v19.3 prepara l'integrazione diretta con AI OPS v19.3:

- pod crash visualizzato nella topology
- RCA disponibile nel tab AI OPS
- AUTO FIX può usare YAML e contesto selezionato
- eventi Kubernetes vengono mostrati sia come timeline visuale sia come input per anomaly analysis

## File principali

- `frontend/src/app/app.component.html`
- `frontend/src/app/app.component.ts`
- `frontend/src/app/app.component.css`
- `frontend/src/assets/nebulaops-v19-3-kubernetes-visual-cluster.svg`
- `docs/diagrams/nebulaops-v19-3-kubernetes-visual-cluster.svg`
