# NebulaOps v20.3.1 Build Fix

Fixes applied after v20.3 Docker build failure:

- Replaced invalid Angular component reference `this.cveItems.set(...)` with `this.cveDashboard.set(...)`.
- Removed unnecessary optional chaining in Kubernetes drilldown template for `selectedNode().label`
  and `selectedNode().status`.
- Kept DevSecOps dynamic API binding intact: `/api/platform/devsecops` now updates security scans, CVE dashboard,
  compliance controls and threat points.

Expected result:

```bash
docker compose build frontend
# or
cd frontend && npm install && npm run build
```

The previous TS2339 error for `cveItems` is resolved.
