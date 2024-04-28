# NebulaOps v15.1 Fix Notes

This patch focuses on two areas reported during local WSL validation:

## Grafana reliability

- `scripts/wsl/stop.sh` now uses the root `docker-compose.yml` and the `nebulaops-v15` compose project,
  matching `start.sh`.
- The root compose file defines a stable Docker network name: `nebulaops-network`.
- Grafana now has:
    - persistent volume `grafana-data:/var/lib/grafana`
    - provisioning mounted at `/etc/grafana/provisioning`
    - dashboards mounted at `/etc/grafana/dashboards`
    - healthcheck against `/api/health`
    - Prometheus datasource UID `prometheus`
- Dashboard provisioning now points to `/etc/grafana/dashboards`.

## Frontend upgrade

- Hero area upgraded to an interactive 3D/holographic local control plane.
- Overview page now includes a 3D control surface for Docker, Kubernetes, Helm and Grafana.
- MongoDB, RabbitMQ and Redis nodes open their real local consoles.
- Docker, Kubernetes, Helm and Grafana nodes route to their internal functional panels.

## Run

```bash
./scripts/wsl/stop.sh
./scripts/wsl/start.sh
./scripts/wsl/smoke-test.sh
```

Open:

- Frontend: <http://localhost:4200>
- Grafana: <http://localhost:3000> with `admin/admin`
- Prometheus: <http://localhost:9090>
- Mongo Express: <http://localhost:8088>
- RabbitMQ UI: <http://localhost:15672>
- Redis Commander: <http://localhost:8089>
