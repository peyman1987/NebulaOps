# NebulaOps frontend shared libraries

v23.2 introduces shared runtime/library boundaries for MFE work:

- `nebulaops-mfe-runtime`: runtime envelope, live-only payload validation and state normalization.
- `nebulaops-api-client`: timeout-aware browser API helper with optional JWT forwarding.
- `nebulaops-live-state`: small live-state snapshot helpers.

These libraries intentionally avoid mock/static operational data. They are lightweight source libraries that can be imported by shell/remotes as the MFE codebase is consolidated.
