# NebulaOps — WSL Ubuntu Run Guide

Guide for Windows 11 + WSL2 Ubuntu.

## Recommended setup

Use the Linux filesystem, not `C:\`:

```bash
mkdir -p ~/projects
cd ~/projects
unzip /mnt/c/Users/<your-user>/Downloads/nebulaops-portfolio-v8-wsl-verified.zip
cd nebulaops-mongo-angular-kafka
```

Running from `/mnt/c/...` works, but Docker and Angular builds are slower.

## Requirements

Install on Windows:

1. WSL2 Ubuntu
2. Docker Desktop
3. Docker Desktop → Settings → Resources → WSL Integration → enable Ubuntu

Inside Ubuntu:

```bash
chmod +x scripts/*.sh scripts/wsl/*.sh
./scripts/wsl/install-prereqs-ubuntu.sh
./scripts/wsl/check-wsl.sh
```

## Start

```bash
./scripts/wsl/start.sh
```

Services:

```text
Frontend:   http://localhost:4200
Gateway:    http://localhost:8080/actuator/health
Grafana:    http://localhost:3000  admin/admin
Prometheus: http://localhost:9090
MongoDB:    localhost:27017
Kafka:      localhost:9092
```

## Smoke test

```bash
./scripts/wsl/smoke-test.sh
```

It registers a user, creates a task via the gateway, stores it in MongoDB, publishes a Kafka event, and reads
notifications.

## Useful commands

```bash
./scripts/wsl/status.sh
./scripts/wsl/logs.sh
./scripts/wsl/logs.sh task-service
./scripts/wsl/stop.sh
./scripts/wsl/reset.sh
```

## Recommended .wslconfig

Create `C:\Users\<your-user>\.wslconfig` on Windows:

```ini
[wsl2]
memory=8GB
processors=4
swap=4GB
localhostForwarding=true
```

Restart from PowerShell:

```powershell
wsl --shutdown
```

## Troubleshooting

Docker daemon not reachable: start Docker Desktop and enable WSL Integration.

Ports already in use:

```bash
ss -tulpn | grep -E '3000|4200|8080|8081|8082|8083|8084|9090|9092|27017'
```

Kafka can take 30-90 seconds to start on first run:

```bash
./scripts/wsl/logs.sh kafka
```

For better performance, keep project under `~/projects`, not `/mnt/c`.

## v9 services

After startup, also check:

- Go Cache Service: http://localhost:8091/health
- RabbitMQ Management: http://localhost:15672, username `guest`, password `guest`
- Redis: exposed on localhost:6379 for local debugging

Example cache test:

```bash
curl -X PUT http://localhost:8091/cache/dashboard:summary \
  -H 'content-type: application/json' \
  -d '{"value":"{\"openTasks\":12}","ttlSeconds":120}'
curl http://localhost:8091/cache/dashboard:summary
```

## Author

**Peyman Eshghi Malayeri**  
Email: peyman_em@yahoo.com  
Project Year: 2024
