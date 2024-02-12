# Start Here — NebulaOps

This guide helps you run, inspect and present NebulaOps as a professional cloud-native portfolio project.

## 1. What this project demonstrates

NebulaOps demonstrates a realistic SaaS-style platform with:

- Angular frontend
- Spring Cloud Gateway API edge
- Spring Boot microservices
- Go infrastructure services
- MongoDB persistence
- RabbitMQ event queues
- Redis cache
- Prometheus and Grafana observability
- Docker Compose local runtime
- Helm and Argo CD deployment assets
- GitLab CI pipeline structure

The architecture is intentionally optimized without Kafka. RabbitMQ is the single broker for asynchronous workflows.

## 2. Recommended local path

For WSL2, keep the source code inside the Linux filesystem:

```bash
mkdir -p ~/projects
cp -r /mnt/d/workspace/personal/portfolio/nebulaops-v13 ~/projects/nebulaops
cd ~/projects/nebulaops
```

## 3. Start the platform

```bash
chmod +x scripts/*.sh scripts/wsl/*.sh
./scripts/wsl/check-wsl.sh
./scripts/wsl/start.sh
```

## 4. Verify the platform

```bash
./scripts/wsl/smoke-test.sh
./scripts/verify-local.sh
```

Open:

- Frontend: http://localhost:4200
- Gateway: http://localhost:8080
- RabbitMQ: http://localhost:15672
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000

## 5. How to present the project

A strong interview narrative:

> NebulaOps is a cloud-native microservice platform that uses Angular, Spring Boot, Go, MongoDB, Redis and RabbitMQ. I
> designed the architecture around clear service boundaries, a lightweight event-driven model, local Docker
> productivity,
> production-style observability and Kubernetes-ready deployment assets.

Recommended presentation order:

1. Show the animated runtime architecture.
2. Explain why the platform uses a single broker: RabbitMQ.
3. Open the Angular dashboard.
4. Trigger API calls through the gateway.
5. Show RabbitMQ queues and service logs.
6. Show Prometheus and Grafana.
7. Explain Helm and Argo CD deployment flow.
8. Close with the quality report and roadmap.

## 6. Important files

| File                                | Purpose                        |
|-------------------------------------|--------------------------------|
| `docker-compose.yml`                | Main local runtime definition  |
| `infrastructure/docker-compose.yml` | Supporting infrastructure view |
| `scripts/wsl/start.sh`              | WSL-friendly startup script    |
| `scripts/smoke-test.sh`             | Local verification script      |
| `docs/diagrams/*.svg`               | Animated architecture diagrams |
| `helm/nebulaops`                    | Kubernetes Helm chart          |
| `.gitlab-ci.yml`                    | CI pipeline skeleton           |
| `argocd`                            | GitOps application assets      |
