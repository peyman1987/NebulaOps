# Real Kubernetes Console

NebulaOps v17 uses kubectl through the gateway to provide real Kubernetes operations from the frontend.

## Supported operations

- List namespaces, pods, deployments, replica sets, stateful sets, daemon sets, services, ingress, config maps, secrets
  and cron jobs.
- Read YAML for a selected resource.
- Apply edited YAML.
- Create resources from the UI.
- Delete resources.
- Scale scalable workloads.
- Read Kubernetes logs when Docker Compose logs are not available.

## Local cluster recommendation

Use kind:

```bash
./scripts/linux/create-kind-cluster.sh nebulaops-v17
```

Then mount kubeconfig into the gateway through `.kube/config` and restart the platform.
