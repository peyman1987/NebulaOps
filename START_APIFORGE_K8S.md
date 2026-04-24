# APIForge on Kubernetes

Build and deploy APIForge directly to the active Kubernetes context:

```bash
./scripts/wsl/deploy-apiforge-k8s.sh
```

Open:

```text
http://localhost:31110/apiforge/
```

Health check:

```bash
curl http://localhost:31110/apiforge/actuator/health
```

Remove runtime resources while keeping APIForge data:

```bash
./scripts/wsl/undeploy-apiforge-k8s.sh
```
