# WSL2 Development Guide

## Recommended environment

- Windows 11
- WSL2 with Ubuntu
- Docker Desktop with WSL integration enabled
- At least 10 GB memory allocated to Docker Desktop
- Project stored under `~/projects`, not `/mnt/c` or `/mnt/d`

## Pre-flight check

```bash
./scripts/wsl/check-wsl.sh
```

## Start

```bash
./scripts/wsl/start.sh
```

## Smoke test

```bash
./scripts/wsl/smoke-test.sh
```

## Stop

```bash
./scripts/wsl/stop.sh
```

## Performance guidance

Avoid daily development under Windows-mounted paths such as `/mnt/c` or `/mnt/d`. Docker bind mounts, Node package
installation and Java builds are faster and more reliable inside the WSL Linux filesystem.

## Troubleshooting

### Docker daemon unreachable

Start Docker Desktop and enable WSL integration for the Ubuntu distribution.

### Port already allocated

Find the process using the port and stop it, or update the exposed port in Docker Compose.

### RabbitMQ not healthy

Inspect logs:

```bash
docker logs nebulaops-rabbitmq-1
```

Then restart:

```bash
docker compose restart rabbitmq
```

### Full reset

```bash
docker compose down -v
docker system prune -f
./scripts/wsl/start.sh
```
