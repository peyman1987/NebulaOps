# NebulaOps v15 Feature Matrix

| Area                 | v15 capability                                    | Real backend action                            |
|----------------------|---------------------------------------------------|------------------------------------------------|
| Docker containers    | List/start/stop/restart/remove                    | Docker CLI through gateway and Docker socket   |
| Docker images        | List local images                                 | `docker images`                                |
| Docker stats         | CPU/memory/network table                          | `docker stats --no-stream`                     |
| Kubernetes resources | List and filter resources                         | `kubectl get ... -o json`                      |
| Kubernetes YAML      | Read/edit/apply YAML                              | `kubectl get -o yaml` and `kubectl apply -f -` |
| Kubernetes scale     | Scale workloads                                   | `kubectl scale`                                |
| Kubernetes logs      | Docker Compose logs then Kubernetes logs fallback | `docker compose logs` / `kubectl logs`         |
| Helm                 | List and uninstall releases                       | `helm list`, `helm uninstall`                  |
| Grafana              | Health and dashboard discovery                    | Grafana HTTP API                               |
| Observability        | Prometheus/Grafana stack                          | Compose services and provisioning              |
| WSL/Linux setup      | No Docker Desktop dependency                      | Native Docker Engine installer                 |

## Local service map deep links

The v15 infrastructure tab now behaves as an operational launcher, not only as a visual map. Each service card can be
clicked and redirects to the real control surface used by that component:

| Service        | Action from FE                            | URL / Tab                             |
|----------------|-------------------------------------------|---------------------------------------|
| MongoDB        | Opens Mongo Express                       | http://localhost:8088                 |
| RabbitMQ       | Opens RabbitMQ Management                 | http://localhost:15672                |
| Redis          | Opens Redis Commander                     | http://localhost:8089                 |
| Prometheus     | Opens Prometheus UI and NebulaOps logs    | http://localhost:9090 / OBSERVABILITY |
| Grafana        | Opens Grafana and NebulaOps dashboard tab | http://localhost:3000 / GRAFANA       |
| Kubernetes API | Opens the Kubernetes control panel tab    | KUBERNETES                            |
| Helm           | Opens the Helm releases control panel tab | HELM                                  |

Default local credentials for the newly added UI tools are `admin/admin` for Mongo Express and Redis Commander. RabbitMQ
keeps `guest/guest`; Grafana keeps `admin/admin` for the local developer profile.
