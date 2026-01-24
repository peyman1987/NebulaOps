# NebulaOps v22.4 Release Notes

## Delivery and task management

- Added a TASK Kanban board tab with four workflow columns: To start, In progress, To test and Done.
- Added create, remove and drag-and-drop move actions for tasks.
- Added persistent task ordering through `sortOrder` and a dedicated `PATCH /api/tasks/{id}/move` endpoint.
- Extended task events with title, status, assignee and notification message payloads.
- Notification service now renders task-specific assignment and movement messages from RabbitMQ events.

## Identity administration

- Added a new Identity Admin micro frontend for Keycloak realm users, groups and roles.
- Added Keycloak realm API endpoints in auth-service under `/api/identity/realms/{realm}/...`.
- Added create, update and disable operations for users, groups and roles.
- Added Redis-backed list cache for Keycloak users, groups and roles with mutation invalidation.
- Added gateway proxy routes for Identity Admin APIs and the shell navigation entry.

## Runtime and packaging

- Updated project metadata, image names, JWT storage keys and frontend bundle versioning to v22.4.
- Added the `mfe-identity-admin` runtime service and static shell copy path.
- Fixed the remote entry generator so remote builds use the remote working directory correctly.
- Rebuilt the shell, Task Management remote and Identity Admin remote distributions.

## Verification performed

- Frontend shell production build completed.
- Task Management remote production build completed.
- Identity Admin remote production build completed.
- Classic remote verification completed for all remotes, including Identity Admin.
- Package validation completed successfully.
- YAML validation completed successfully.
- Go cache-service and event-worker tests completed successfully.

Backend Maven compilation was not executed in this sandbox because neither host Maven nor Dockerized Maven is available here. The backend code has been kept inside the existing Spring Boot structure and the provided WSL backend build script remains the authoritative build path.
