# NebulaOps v22.5 Extensions

Installed extensions in this package:

| Extension | Path | UI control | Open URL |
|---|---|---|---|
| APIForge | `extensions/apiforge` | `/api/extensions/console?extension=apiforge` | `/apiforge/` |
| KubeBridge | `extensions/kubebridge` | `/api/extensions/console?extension=kubebridge` | `/kubebridge/` |
| Contract Hub | `extensions/contract-hub` | `/api/extensions/console?extension=contract-hub` | `/contract-hub/` |

Each extension is a real Spring Boot MVC project with its own Dockerfile and Kubernetes manifest under `k8s/deployment.yml`.

The UI control plane supports:

```text
Start
Stop
Restart
Status
Open
```

No mock records or static operational data are generated. Missing integrations must be displayed as explicit runtime states such as `NOT_CONFIGURED`, `UNAVAILABLE` or `DEGRADED`.
