# NebulaOps v19.3 Feature Matrix

| Area              | Feature           | Functional behavior                                                        |
|-------------------|-------------------|----------------------------------------------------------------------------|
| Docker            | Containers        | List, start, stop, restart and remove through gateway endpoints            |
| Docker            | Images and stats  | Reads local Docker Engine data through mounted Docker socket               |
| Kubernetes        | Resource explorer | Lists local resources from kubectl-backed snapshot                         |
| Kubernetes        | YAML editor       | Applies YAML through backend API                                           |
| Kubernetes        | Scale             | Patches supported workload replicas                                        |
| Helm              | Releases          | Reads Helm releases and supports uninstall                                 |
| Grafana           | Health            | Reads Grafana API through gateway                                          |
| Grafana           | Dashboards        | Lists provisioned dashboards and opens Grafana                             |
| Observability     | Logs              | Manual and automatic refresh                                               |
| Local service map | Links             | Opens Mongo Express, RabbitMQ, Redis Commander, Prometheus and Grafana     |
| UI                | Spatial 3D        | CSS 3D animated hero and service galaxy without extra runtime dependencies |
| WSL/Linux         | Native stack      | Docker Engine, Compose, kubectl and Helm without Docker Desktop            |
