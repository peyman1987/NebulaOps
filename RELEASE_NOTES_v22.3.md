# Release notes v22.3

## Runtime alignment

- The local browser entry point is `http://nebulaops.localhost`.
- Micro frontend standalone pages are served from `/remotes/<mfe>/` on the same origin.
- Shell remote entries are served from `/remotes/<mfe>/remoteEntry.js` as static assets in the frontend image.
- Gateway, Keycloak, Grafana and Prometheus are exposed through reverse-proxy paths.
- Obsolete documentation and SVGs referencing earlier local port layouts were removed.

## Developer hygiene

- Added root `.gitignore` for the polyglot repository.
- Updated docs to reflect the current reverse-proxy runtime and MFE route model.
