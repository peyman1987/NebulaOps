# NebulaOps v15 Architecture

NebulaOps v15 is a single-machine DevOps control plane designed for WSL Ubuntu or native Linux without Docker Desktop.
It uses native Docker Engine, Docker Compose plugin, kubectl, Helm, kind, Prometheus and Grafana.

## Main design goals

- Run completely on a personal workstation.
- Expose real operational controls from the Angular frontend.
- Keep all destructive actions explicit and local.
- Provide Docker Desktop-like visibility without Docker Desktop.
- Provide OpenLens-like Kubernetes exploration with kubectl behind the gateway.
- Keep the system understandable for learning and portfolio presentation.

## Runtime layers

1. **Angular frontend** exposes tabs for Overview, Tasks, Docker, Kubernetes, Helm, Grafana, Observability, CI/CD,
   Security and Infra.
2. **Spring Cloud Gateway** proxies business services and owns local runtime adapters.
3. **Spring Boot services** provide auth, task, notification and file domains.
4. **Go services** provide cache/event worker examples with Redis and RabbitMQ.
5. **Native Docker Engine** runs the local Compose platform.
6. **kind Kubernetes** provides a local Kubernetes cluster without Docker Desktop.
7. **Helm** deploys the platform chart to Kubernetes.
8. **Prometheus and Grafana** provide metrics and dashboards.

## Functional control-plane APIs

The gateway exposes these API families:

- `/api/runtime/docker/containers`: list real local containers.
- `/api/runtime/docker/containers/{id}/{action}`: start, stop, restart, pause, unpause, kill or remove containers.
- `/api/runtime/docker/images`: list real Docker images.
- `/api/runtime/docker/stats`: read real runtime CPU/memory/network stats.
- `/api/kubernetes/snapshot`: list real Kubernetes resources with kubectl.
- `/api/kubernetes/yaml/apply`: apply YAML directly through kubectl.
- `/api/kubernetes/resources/{id}/scale`: scale workloads.
- `/api/runtime/helm/releases`: list real Helm releases.
- `/api/runtime/helm/releases/{release}/uninstall`: uninstall a Helm release.
- `/api/runtime/grafana/health`: read Grafana health API.
- `/api/runtime/grafana/dashboards`: read provisioned Grafana dashboards.

## Security model

This project intentionally gives the gateway access to `/var/run/docker.sock` and a kubeconfig so the frontend can
manage local resources. This is powerful and should be used only on a personal development machine. Do not expose the
gateway port to the public internet.

## Recommended local topology

- Host OS: Windows 11 + WSL Ubuntu, or Ubuntu Linux.
- Container runtime: native Docker Engine inside WSL/Linux.
- Kubernetes: kind cluster named `nebulaops-v15`.
- Compose project: `nebulaops-v15`.
- UI: `http://localhost:4200`.
- Gateway: `http://localhost:8080`.
- Grafana: `http://localhost:3000`.
- Prometheus: `http://localhost:9090`.
