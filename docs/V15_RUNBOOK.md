# NebulaOps v16 Runbook

## 1. Install native tools on WSL or Linux

```bash
./scripts/wsl/install-native-toolchain.sh
```

For native Ubuntu/Linux:

```bash
./scripts/linux/install-native-toolchain.sh
```

After WSL installation, run this from PowerShell and reopen Ubuntu:

```powershell
wsl --shutdown
```

## 2. Create the local Kubernetes cluster

```bash
./scripts/linux/create-kind-cluster.sh nebulaops-v16
```

## 3. Start the Compose platform

```bash
./scripts/linux/start-native.sh
```

Open:

- Frontend: `http://localhost:4200`
- Gateway: `http://localhost:8080`
- Grafana: `http://localhost:3000` with `admin/admin`
- Prometheus: `http://localhost:9090`
- RabbitMQ: `http://localhost:15672` with `guest/guest`

## 4. Deploy the Helm chart

```bash
./scripts/linux/helm-install-nebulaops.sh nebulaops
```

## 5. Verify functional UI tabs

### Docker tab

Use the Docker tab to refresh containers, start/stop/restart a container, inspect images and see live stats.

### Kubernetes tab

Use the Kubernetes tab to list namespaces, pods, deployments, services, ingress, config maps, secrets, stateful sets,
daemon sets and cron jobs. Select a resource to load YAML, edit it and apply it.

### Helm tab

Use the Helm tab to read installed Helm releases and uninstall a release from the selected namespace.

### Grafana tab

Use the Grafana tab to check Grafana health and list provisioned dashboards.

### Observability tab

Use the Observability tab to refresh logs manually or enable auto-refresh.

## 6. Stop

```bash
docker compose down
```

To delete the kind cluster:

```bash
kind delete cluster --name nebulaops-v16
```
