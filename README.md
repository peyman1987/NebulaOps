# NebulaOps v15

NebulaOps v15 is an advanced DevOps portfolio platform for a personal workstation. It runs on WSL Ubuntu or native Linux
with native Docker Engine, Docker Compose plugin, kubectl, Helm and kind. Docker Desktop is not required.

## What changed in v15

- Native WSL/Linux install scripts for Docker Engine, Docker Compose, kubectl, Helm and kind.
- Docker Desktop-like frontend tab backed by real Docker CLI operations.
- OpenLens-like Kubernetes frontend tab backed by real kubectl operations.
- Helm frontend tab backed by real Helm release operations.
- Grafana frontend tab backed by the real Grafana HTTP API.
- More advanced architecture docs, runbook, feature matrix and animated SVG.
- Updated gateway runtime adapters for Docker, Helm and Grafana.

## Main stack

- Angular frontend
- Spring Boot and Spring Cloud Gateway
- Spring Boot microservices: auth, task, notification, file
- Go services: cache and event worker
- MongoDB, RabbitMQ, Redis
- Docker Engine and Docker Compose plugin
- kind Kubernetes, kubectl and Helm
- Prometheus and Grafana

## Start on WSL Ubuntu without Docker Desktop

```bash
./scripts/wsl/install-native-toolchain.sh
```

Then from PowerShell:

```powershell
wsl --shutdown
```

Reopen Ubuntu and run:

```bash
./scripts/linux/create-kind-cluster.sh nebulaops-v15
./scripts/linux/start-native.sh
```

Open:

- Frontend: http://localhost:4200
- Gateway: http://localhost:8080
- Grafana: http://localhost:3000
- Prometheus: http://localhost:9090
- RabbitMQ: http://localhost:15672

## Start on native Ubuntu/Linux

```bash
./scripts/linux/install-native-toolchain.sh
./scripts/linux/create-kind-cluster.sh nebulaops-v15
./scripts/linux/start-native.sh
```

## Deploy with Helm

```bash
./scripts/linux/helm-install-nebulaops.sh nebulaops
```

## Important security note

The gateway mounts the Docker socket and kubeconfig to provide real local runtime control from the frontend. This is
intended only for a local development machine. Do not expose the gateway publicly.

## Documentation

- `docs/V15_ARCHITECTURE.md`
- `docs/V15_RUNBOOK.md`
- `docs/V15_FEATURE_MATRIX.md`
- `docs/nebulaops-v15-architecture-animated.svg`
- `docs/TECHNICAL_DOCUMENTATION.md`
- `docs/TROUBLESHOOTING.md`

### v15 Local Service Map deep links

In the **INFRA** tab, service cards are clickable and open the real related dashboard or NebulaOps control panel:
MongoDB opens Mongo Express on `localhost:8088`, Redis opens Redis Commander on `localhost:8089`, RabbitMQ opens its
management UI on `localhost:15672`, Prometheus opens `localhost:9090`, Grafana opens `localhost:3000`, and
Kubernetes/Helm route to the internal NebulaOps control tabs.
