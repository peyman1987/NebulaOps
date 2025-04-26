# NebulaOps v21.1 Task REST Fix

This release fixes the Tasks UI regression.

## What changed

The Tasks page no longer uses localStorage or static seed tasks. It now performs real REST calls through the Gateway
to `task-service`:

- `GET /api/tasks?organizationId=default-org`
- `POST /api/tasks`
- `PATCH /api/tasks/{id}/status/{status}`
- `DELETE /api/tasks/{id}`

## Runtime path

Browser → Nginx frontend proxy → Gateway `/api/tasks/**` → `task-service:8082` → MongoDB.

## Expected behavior

- Empty task database shows an empty state, not fake sample tasks.
- Creating a task persists it in MongoDB.
- Dragging a task between columns calls the status REST endpoint.
- Deleting a task calls the delete REST endpoint.

## Verification

Open DevTools Network tab and filter by `tasks`. You should see calls to `/api/tasks` when opening the Tasks section,
creating, dragging, or deleting tasks.
