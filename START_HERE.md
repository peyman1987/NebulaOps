# Start here: NebulaOps v15

1. Install native tooling without Docker Desktop:

```bash
./scripts/wsl/install-native-toolchain.sh
```

2. Restart WSL from PowerShell:

```powershell
wsl --shutdown
```

3. Create the local cluster:

```bash
./scripts/linux/create-kind-cluster.sh nebulaops-v15
```

4. Start the platform:

```bash
./scripts/linux/start-native.sh
```

5. Open the frontend:

```text
http://localhost:4200
```

Use the Docker, Kubernetes, Helm and Grafana tabs to control real local resources.
