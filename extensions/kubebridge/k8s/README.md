# Kubernetes deployment — kubebridge

This folder contains the extension-local Kubernetes manifest for `kubebridge`.

Apply directly from the extension folder:

```bash
kubectl apply -f k8s/deployment.yml
```

Or from the NebulaOps project root:

```bash
kubectl apply -f extensions/kubebridge/k8s/deployment.yml
```

The manifest is self-contained: it includes the `nebulaops` namespace plus the extension Deployment, Service, probes and any required RBAC/PVC resources.

The central copy under `infrastructure/kubernetes` is kept only for platform-level bulk deployment compatibility. The deploy script uses this extension-local manifest first.
