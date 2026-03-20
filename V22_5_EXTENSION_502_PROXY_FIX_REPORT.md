# NebulaOps v23.1 — Extension 502 Proxy Guard Fix

## Scope

This package fixes the browser-facing `502 Bad Gateway` shown when opening an installed extension path such as `/apiforge/` before the extension proxy is ready.

## Changes

- Added `frontend/dist/nebulaops/browser/extension-unavailable.html` with NebulaOps styling.
- Added Nginx `proxy_intercept_errors` for installed extension paths:
  - `/apiforge/`
  - `/kubebridge/`
  - `/contract-hub/`
  - `/extensions/apiforge/`
  - `/extensions/kubebridge/`
  - `/extensions/contract-hub/`
- Added friendly fallback for `502`, `503`, and `504` instead of the raw Nginx error page.
- Updated `ExtensionControlController` to return explicit `EXTENSION_NOT_RUNNING`, `GATEWAY_PROXY_UNAVAILABLE`, or `UPSTREAM_REQUEST_FAILED` states rather than failing with an opaque proxy error.
- Added `scripts/wsl/verify-extension-proxy-guard.sh` and wired it into preflight.

## Runtime behavior

NebulaOps core starts normally with:

```bash
./scripts/wsl/start.sh --rebuild
```

Extensions are controlled from the UI:

```text
Sidebar -> Extensions -> Start / Stop / Restart / Status / Open
```

Directly opening `/apiforge/` before APIForge is running no longer shows the raw Nginx `502 Bad Gateway`; it shows a controlled NebulaOps extension status page with a link back to the Extensions control panel.

## Data policy

No mock/static operational data was introduced. Extension state remains live-only from Kubernetes/gateway/runtime checks.
