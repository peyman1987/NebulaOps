# NebulaOps v21.4 — Release Notes

## 🔑 Keycloak Integration

This release introduces **Keycloak 24** as the Identity & Access Management layer for NebulaOps.

---

### What's new

#### Infrastructure — docker-compose
- Added `keycloak-db` service (PostgreSQL 16-alpine) as Keycloak's persistent store
- Added `keycloak` service (`quay.io/keycloak/keycloak:24.0.4`) on port **8180**
  - Auto-imports realm `nebulaops` from `infrastructure/keycloak/realm-nebulaops.json`
  - Starts in `start-dev` mode (HTTP, no TLS) — suitable for local & staging
- Added `KEYCLOAK_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_CLIENT_ID`, `KEYCLOAK_CLIENT_SECRET` to `gateway-service` environment
- `gateway-service` now depends on `keycloak` (waits for health check)
- New volume `keycloak-db-data` for Postgres persistence

#### Realm — `infrastructure/keycloak/realm-nebulaops.json`
- Realm `nebulaops` pre-configured and auto-imported on first boot
- Two Keycloak clients:
  - `nebulaops-frontend` — public PKCE client, redirect to localhost:4200
  - `nebulaops-gateway` — confidential service-account client for backend introspection
- Three realm roles: `nebula-admin`, `nebula-operator`, `nebula-viewer`
- Demo users pre-seeded: **admin/admin** (nebula-admin) and **peyman/admin** (nebula-operator)

#### Frontend
- **Keycloak Admin Console** button added in the top-right session bar (🔑 Keycloak)
- Same button added to the **quick-actions** row in the hero section
- **Keycloak Admin** added as first entry in the INFRA links panel
- `openKeycloakAdmin()` method opens `http://localhost:8180/admin/nebulaops/console` in a new tab
- All version strings updated to **v21.4**

---

### How to access Keycloak

| URL | Purpose |
|-----|---------|
| `http://localhost:8180/admin/nebulaops/console` | Realm admin console |
| `http://localhost:8180/admin` | Master admin console |
| `http://localhost:8180/realms/nebulaops/.well-known/openid-configuration` | OIDC discovery |

Default credentials: **admin / admin**

---

### Migration from v21.3

No breaking changes. Keycloak is an additive service. Existing auth-service JWT flow is unchanged.

To start with Keycloak:
```bash
docker compose up -d keycloak-db keycloak
# wait ~30s for realm import, then start the rest
docker compose up -d
```

---

### New environment variables

```env
KEYCLOAK_CLIENT_SECRET=nebulaops-secret-dev-only   # override in production
```
