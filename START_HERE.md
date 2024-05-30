# Start Here — NebulaOps v17

This guide starts the complete local platform on WSL Ubuntu or Linux.

## 1. Enter the project

```bash
cd nebulaops-v17
```

## 2. Install the native toolchain

```bash
./scripts/wsl/install-native-toolchain.sh
```

## 3. Start the platform

```bash
./scripts/wsl/start.sh
```

## 4. Validate the environment

```bash
./scripts/wsl/smoke-test.sh
```

## 5. Open the main tools

| Tool                   | URL                    |
|------------------------|------------------------|
| Angular Control Center | http://localhost:4200  |
| Grafana                | http://localhost:3000  |
| Prometheus             | http://localhost:9090  |
| RabbitMQ Management    | http://localhost:15672 |

Frontend demo login: `admin / admin`.

## Troubleshooting

Check Grafana provisioning:

```bash
docker compose -p nebulaops-v17 logs --tail=200 grafana
grep -R "isDefault: true" -n infrastructure/observability/grafana/provisioning/datasources
```

There must be exactly one default datasource.

Check the frontend build:

```bash
cd frontend
npm install
npm run build -- --configuration production
```

Check all containers:

```bash
docker compose -p nebulaops-v17 ps
```
