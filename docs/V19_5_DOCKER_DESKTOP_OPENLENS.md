# NebulaOps v19.5 — Docker Desktop + OpenLens Console

Questa build reintegra la sezione container delle versioni precedenti e la porta al livello v19.5.

## Nuovo tab: CONTAINERS

Il tab `CONTAINERS` funziona come una console integrata ispirata a Docker Desktop e OpenLens.

### Docker Desktop-like

- Lista container locali con stato runtime.
- Start, stop, restart e pause simulation.
- CPU, memoria, porte esposte e network.
- Log container selezionato.
- Immagini locali con tag, size e vulnerabilità.
- Volumi persistenti MongoDB, Redis e Grafana.
- Prune sicuro della cache immagini.

### OpenLens-like Kubernetes

- Vista controller Kubernetes: Deployment, StatefulSet, DaemonSet, Ingress.
- Scale up/down dei pod.
- Restart rollout simulato.
- Inspect di service, ingress, pod e controller.
- Azioni rapide stile Lens con comando kubectl associato.
- Terminale integrato Docker + kubectl.

## INFRA Integration

Il tab `INFRA` ora contiene collegamenti diretti anche verso:

- Docker Desktop Console interna.
- OpenLens Console interna.
- Kubernetes Console 3D.
- Grafana, Redis Commander, Mongo Express, RabbitMQ, Prometheus, Loki, Tempo e OpenTelemetry.

## Portfolio value

Questa parte mostra capacità senior su:

- container runtime operations;
- Kubernetes workload management;
- cluster troubleshooting;
- scaling/rollout operations;
- observability + runtime correlation;
- local-first DevOps platform design.
