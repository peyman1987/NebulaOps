# NebulaOps v21.3 — Release Notes

## Highlights

v21.3 introduces **real JWT authentication**, **full Docker and Kubernetes runtime actions**, and a refreshed **3D/animation FE layer**.

---

## 1. JWT Authentication (auth-service)

- `POST /api/auth/login` now returns a real **HS256-signed JWT** (JJWT 0.12.6)
  - `accessToken` (24h), `refreshToken` (7d), `tokenType: Bearer`, `expiresIn`
- `POST /api/auth/register` validates password length (min 6 chars)
- `GET  /api/auth/me` validates Bearer token and returns user claims
- `POST /api/auth/refresh` issues new access token from refresh token
- `POST /api/auth/logout` — stateless (client deletes token; Redis blacklist hook ready)
- Dev-mode shortcut: `admin/admin` always works without MongoDB
- `JWT_SECRET` environment variable (min 32 chars) — defaults to dev placeholder
- Frontend: `login()` calls the real API, stores JWT in localStorage, passes `Authorization: Bearer` header on all subsequent requests

## 2. Docker Desktop — Real Container Actions (gateway-service)

New controller: `DockerActionsController` — all calls via Unix socket `/var/run/docker.sock`:

| Endpoint | Action |
|---|---|
| `POST /api/runtime/docker/containers/{id}/start` | Start container |
| `POST /api/runtime/docker/containers/{id}/stop` | Stop container |
| `POST /api/runtime/docker/containers/{id}/restart` | Restart container |
| `POST /api/runtime/docker/containers/{id}/pause` | Pause container |
| `POST /api/runtime/docker/containers/{id}/unpause` | Unpause container |
| `DELETE /api/runtime/docker/containers/{id}` | Remove container |
| `GET /api/runtime/docker/containers/{id}/logs` | Fetch container logs |
| `GET /api/runtime/docker/containers/{id}/stats` | Container stats |
| `DELETE /api/runtime/docker/images/{id}` | Remove image |
| `POST /api/runtime/docker/images/prune` | Prune dangling images |
| `DELETE /api/runtime/docker/volumes/{name}` | Remove volume |
| `POST /api/runtime/docker/volumes/prune` | Prune unused volumes |
| `POST /api/runtime/docker/networks/prune` | Prune unused networks |

Frontend: each container card shows inline action buttons (Start/Restart/Stop/Pause/Logs/Remove) with optimistic UI update and real API call with rollback on failure.

## 3. OpenLens — Real Kubernetes Actions (gateway-service)

New controller: `KubernetesActionsController` — all via `ToolCommandClient` (kubectl):

| Endpoint | Action |
|---|---|
| `DELETE /api/kubernetes/pods/{ns}/{name}` | Delete pod (normal or --force) |
| `POST /api/kubernetes/pods/{ns}/{name}/restart` | Restart pod via owner Deployment rollout |
| `GET /api/kubernetes/pods/{ns}/{name}/logs` | Get pod logs (--tail=N) |
| `POST /api/kubernetes/deployments/{ns}/{name}/scale` | Scale replicas |
| `POST /api/kubernetes/deployments/{ns}/{name}/restart` | Rolling restart |
| `GET /api/kubernetes/deployments/{ns}/{name}/yaml` | Get YAML |
| `POST /api/kubernetes/deployments/{ns}/{name}/yaml` | Apply YAML |
| `GET/POST /api/kubernetes/services/{ns}/{name}/yaml` | Get/apply Service YAML |
| `GET/POST /api/kubernetes/ingresses/{ns}/{name}/yaml` | Get/apply Ingress YAML |
| `POST /api/kubernetes/statefulsets/{ns}/{name}/scale` | Scale StatefulSet |
| `POST /api/kubernetes/statefulsets/{ns}/{name}/restart` | Restart StatefulSet |
| `GET/POST /api/kubernetes/configmaps/{ns}/{name}/yaml` | Get/apply ConfigMap |
| `POST /api/kubernetes/nodes/{name}/cordon` | Cordon node |
| `POST /api/kubernetes/nodes/{name}/uncordon` | Uncordon node |
| `POST /api/kubernetes/nodes/{name}/drain` | Drain node |
| `POST /api/kubernetes/namespaces` | Create namespace |
| `POST /api/kubernetes/apply` | Generic `kubectl apply -f` |
| `POST /api/kubernetes/delete` | Generic `kubectl delete -f` |

Frontend: each row in the OpenLens table shows context-sensitive action buttons per kind (Pod: Restart/Logs/Delete; Deployment: Restart/Scale±/YAML; Service/Ingress: YAML; Node: Cordon).

## 4. Frontend — Enhanced 3D and Animations

- **Container items**: per-container action buttons (Start/Restart/Stop/Pause/Logs/Remove) directly in each card, with color-coded status border
- **Lens table**: grid layout with kind/name/namespace/status/actions columns, status pills
- **Cluster nodes**: spring-easing hover with scale + Z translate + glow
- **holo-nodes**: improved hover with scale + glow
- **SVG lines**: continuous dash-flow animation on all topology lines
- **Login page**: 3D aura animation + floating particles background
- **JWT badge**: animated green indicator in the UI when authenticated
- New utility classes: `.act-btn`, `.lens-row`, `.ctr-item`, `.ctr-actions-row`, `.jwt-indicator`, `.version-badge`

## 5. api.config.ts — 30+ new routes

All Docker action URLs and all Kubernetes action URLs are typed in `api.config.ts`.

## 6. Version bump

All files updated to v21.3.0: pom.xml (parent + all services), docker-compose.yml, api.config.ts, app.component.ts, app.component.html, ARCHITECTURE.md, README.md, PROJECT_METADATA.md.

## Upgrade from v21.2

```bash
cd nebulaops-v21.3
./scripts/wsl/start.sh --rebuild-gateway
```

The auth-service must also be rebuilt to include JJWT:
```bash
docker compose build --no-cache auth-service gateway-service
docker compose up -d
```
