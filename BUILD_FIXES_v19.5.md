# NebulaOps v19.5 Docker/OpenLens Build Fixes

This regenerated package fixes the Angular production build errors reported after adding the Docker Desktop + OpenLens
section.

## Fixed

- Repaired `containerTerminalOutput()` template literal in `frontend/src/app/app.component.ts`.
- Replaced broken multiline `.join('...')` fragments with `.join('\n')`.
- Ensured Docker/OpenLens terminal text remains inside a valid TypeScript template string.
- Added typed `ClusterEvent` construction for OpenLens rollout events so `severity` matches `Priority`.
- Confirmed `forkJoin` is imported from `rxjs` together with `of` and `catchError`.

## Notes

The Docker/OpenLens feature remains a local/simulated control plane for portfolio/demo usage, with API hooks left ready
for real Docker/Kubernetes runtime integration.
