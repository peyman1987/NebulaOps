# NebulaOps v17 Troubleshooting

## Docker daemon is not reachable

Start native Docker Engine inside WSL/Linux:

```bash
sudo service docker start
# or, when systemd is enabled
sudo systemctl start docker
```

If permission is denied:

```bash
sudo usermod -aG docker "$USER"
newgrp docker
```

## Compose cannot build services

```bash
docker compose build --no-cache
docker compose up -d
```

## Kubernetes tab is disconnected

Create or select the kind cluster:

```bash
./scripts/linux/create-kind-cluster.sh nebulaops-v17
kubectl config current-context
cp ~/.kube/config .kube/config
```

Restart the gateway:

```bash
docker compose restart gateway-service
```

## Helm tab is empty

Install the chart first:

```bash
./scripts/linux/helm-install-nebulaops.sh nebulaops
```

## Grafana tab is unavailable

```bash
docker compose ps grafana
docker compose logs --tail=80 grafana
```

Default credentials are `admin/admin`.
