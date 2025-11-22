# NebulaOps MFE Core

Shared TypeScript/Angular utilities for all NebulaOps micro frontends.

It centralizes:

- Angular Element bootstrap (`bootstrapNebulaOpsMfe`)
- JWT propagation from the shell session (`nebulaOpsJwtInterceptor`)
- shared Keycloak storage keys
- gateway URL construction helpers

Each remote imports `@nebulaops/mfe-core` instead of duplicating bootstrap and authentication code.


## Docker build resolution

Each Angular remote maps `@nebulaops/mfe-core` first to a local `./libs/...` copy and then to the central `../../libs/...` source. Remote Dockerfiles copy the shared library into the remote workspace before `ng build`; this keeps Angular package resolution anchored to the remote `node_modules` directory and prevents missing `@angular/*` module errors during isolated Docker builds.
