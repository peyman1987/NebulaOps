# NebulaOps v15 Architecture

NebulaOps v15 models a modern internal developer platform while keeping the runtime practical for a single workstation.
The system separates presentation, API gateway, business services, asynchronous processing, cache, persistence and
observability.

## Runtime layers

| Layer             | Components                                         | Purpose                                     |
|-------------------|----------------------------------------------------|---------------------------------------------|
| Experience        | Angular console, Nginx                             | DevOps portal with operational tabs         |
| Edge/API          | Gateway service                                    | API routing, Kubernetes bridge, log access  |
| Business services | Auth, Task, File, Notification                     | Domain APIs implemented with Spring Boot    |
| Event/cache       | RabbitMQ, Go event worker, Go cache service, Redis | Async events and low-latency reads          |
| Persistence       | MongoDB                                            | Service data storage                        |
| Observability     | Prometheus, Grafana                                | Metrics and dashboarding                    |
| Delivery          | GitLab CI, Helm, Argo CD examples                  | Build, validate, render and deploy workflow |

## Local execution strategy

The default runtime is Docker Compose. Kubernetes assets are included under `infrastructure/kubernetes` and Helm assets
under `infrastructure/helm/nebulaops`. This allows the same portfolio to demonstrate both local microservice execution
and Kubernetes deployment knowledge.

## Design decisions

- The gateway is the only service exposed to the frontend API path.
- MongoDB, RabbitMQ and Redis are first-class platform dependencies, not optional afterthoughts.
- Logs are read from Docker socket first and Kubernetes as fallback, which makes the console useful on a personal
  machine.
- The frontend stores local UI state so the demo remains usable when some backend endpoints are unavailable.
- Grafana provisioning is included so dashboards appear automatically after startup.
