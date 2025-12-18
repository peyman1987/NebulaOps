# Troubleshooting

## Shell opens but standalone MFE button uses an old localhost port

The current shell must open routes such as:

```text
/remotes/openlens-kubernetes/
/remotes/task-management/
```

not public 42xx ports. Rebuild and recreate the frontend image:

```bash
./scripts/wsl/build-frontend-local.sh
docker compose build frontend
docker compose up -d --force-recreate frontend
```

Then hard-refresh the browser tab or clear the site data for `nebulaops.localhost`.

## Remote entry check

```bash
curl -I http://nebulaops.localhost/remotes/docker-desktop/remoteEntry.js
curl -I http://nebulaops.localhost/remotes/openlens-kubernetes/remoteEntry.js
curl -I http://nebulaops.localhost/remotes/task-management/remoteEntry.js
```

Expected result is HTTP 200 from the frontend Nginx container.

## Gateway check

```bash
curl -I http://nebulaops.localhost/actuator/health
```

If the shell loads but API widgets fail, check gateway logs:

```bash
./scripts/wsl/logs.sh gateway-service
```

## Keycloak callback issues

The public Keycloak route is:

```text
http://nebulaops.localhost/keycloak
```

If login fails after upgrading from an older package, remove old containers/volumes or reimport the realm so the client redirect URI points to `http://nebulaops.localhost`.
