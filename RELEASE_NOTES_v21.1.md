# NebulaOps v21.1 — Release Notes

## What's new

### API normalization layer (main fix)
Added `NormalizedApiController` in gateway-service that translates live backend
data into the exact shapes the Angular frontend expects:

| Endpoint | v20.6 returned | v21.1 returns |
|---|---|---|
| `/api/runtime/docker/containers` | `{live, items, toolStatus}` | `List` (direct) |
| `/api/runtime/docker/images` | `{live, items, toolStatus}` | `List` (direct) |
| `/api/runtime/docker/volumes` | `{live, items, toolStatus}` | `List` (direct) |
| `/api/runtime/helm/releases` | `{live, data, toolStatus}` | `List` (direct) |
| `/api/kubernetes/snapshot` | raw kubectl cluster-info | `K8sSnapshot {cluster, resources, logs}` |
| `/api/kubernetes/logs` | raw kubectl events | `List<ServiceLog>` |
| `/api/platform/observability` | missing traceFlow/latencyHeatmap | includes all required fields |
| `/api/platform/gitops` | raw argocd JSON | `{state, deploymentWaves, commitStream}` |
| `/api/platform/environments` | `{live, data}` | `List` (direct) |
| `/api/platform/devsecops` | raw trivy JSON | `{scans, cves, controls, threats}` |

### Build fixes
- Parent `pom.xml` version kept at `20.6.0` — child service poms reference it
  by `relativePath` (`../pom.xml`), not from Maven Central. Changing the version
  caused all service builds to fail trying to download a non-existent artifact.

### Version bump across the entire project
- All Docker image tags: `nebulaops-v21-1-*`
- Docker Compose project name: `nebulaops-v21-1`
- Frontend: title, labels, localStorage keys, SVG references
- New SVG assets: `nebulaops-v21-1-*.svg` (copied and updated from v20-2)
- Helm Chart: `version: 21.1.0`, `appVersion: "21.1.0"`
- OpenAPI specs: version `21.1.0`
- All docs/diagrams: version strings updated

## How to start

```bash
./scripts/wsl/start.sh
```

## Tool requirements

`kubectl`, `docker`, `helm` are required and mounted automatically.
`trivy`, `terraform`, `argocd` are optional — endpoints return `live:false`
with `toolStatus` when missing, never mock data.
