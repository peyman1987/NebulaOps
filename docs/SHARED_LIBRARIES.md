# NebulaOps Shared Libraries

NebulaOps v22.2 now separates reusable contracts from feature implementations.

## Frontend: `@nebulaops/mfe-core`

Location: `frontend/libs/nebulaops-mfe-core`.

Purpose:

- one bootstrap function for Angular Elements remotes;
- one JWT interceptor for all MFE HTTP calls;
- shared Keycloak storage keys;
- gateway URL helper.

Every remote `src/main.ts` now imports `bootstrapNebulaOpsMfe` instead of duplicating the same setup.

## Backend: `nebulaops-shared-kernel`

Location: `backend/nebulaops-shared-kernel`.

Purpose:

- shared API envelope;
- shared error envelope;
- shared security constants;
- service identity and REST path helpers.

This module is registered in the Maven reactor and is ready to be used by Spring Boot services when new shared DTOs or helpers are introduced.
