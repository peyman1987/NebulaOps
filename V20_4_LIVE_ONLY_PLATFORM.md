# NebulaOps v20.4 Live-Only Platform

Questa versione rimuove i fallback mock/random dalle sezioni runtime principali.

## Cosa cambia

- Observability legge health HTTP reali di Prometheus, Loki, Tempo, Grafana e OpenTelemetry.
- Docker Desktop legge Docker Engine via `/var/run/docker.sock`.
- OpenLens/Kubernetes legge `kubectl` tramite kubeconfig montato.
- GitOps legge ArgoCD se CLI/API è configurato; altrimenti mostra `Unavailable`, non dati finti.
- DevSecOps usa Trivy/Git/Docker/Kubectl quando disponibili; altrimenti le liste restano vuote o queued.
- Environments vengono generati dalle namespace Kubernetes reali.
- Rimossi random/mock fallback dalle API `/api/platform/*`.

## Nota

Per avere dati pieni servono tool reali disponibili nel container gateway:
Docker socket, kubeconfig, helm, kubectl, trivy, argocd, terraform.
Quando un tool non è configurato la UI mostra stato vuoto/unavailable invece di inventare dati.
