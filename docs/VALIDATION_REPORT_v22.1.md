# NebulaOps v22.1 — Validation Report

## Completed checks

| Check | Result | Command / scope |
| --- | --- | --- |
| YAML files | PASS | `python3 scripts/validate-yaml.py` |
| Shell syntax | PASS | `find scripts -name "*.sh" -print0 | xargs -0 -I{} bash -n {}` |
| Package contract | PASS | `python3 scripts/validate-package.py` |
| GitLab CI YAML parse | PASS | `yaml.safe_load(.gitlab-ci.yml)` |
| Go cache service test | PASS | `go test -vet=off -run Test -count=1 ./internal/cache` |
| Go event worker test | PASS | `go test -vet=off -run Test -count=1 ./internal/events` |
| Frontend TypeScript parser smoke | PASS WITH EXPECTED DEPENDENCY WARNINGS | Global `tsc` parsed edited files; missing Angular/RxJS modules are expected without `node_modules`. |

## Environment limitations

- Maven is not installed in the sandbox, so `cd backend && mvn -B -ntp test` could not be executed here.
- Docker is not installed in the sandbox, so `docker compose config/up` and container health checks could not be executed here.
- `npm install` for Angular dependencies did not complete within the sandbox timeout, so `npm run build` could not be completed here.

## Recommended final local checks

```bash
cd frontend && npm install && npm run build
cd ../backend && mvn -B -ntp test
cd .. && python3 scripts/validate-yaml.py && python3 scripts/validate-package.py
find scripts -name "*.sh" -print0 | xargs -0 -I{} bash -n {}
docker compose config
docker compose up -d --build
./scripts/wsl/health.sh
```

## Patch v22.1 — cost-analytics-service Docker build

- Corretto `backend/cost-analytics-service/Dockerfile`: ora è multi-stage e compila il JAR Maven internamente.
- Risolve l'errore Docker `COPY target/*.jar app.jar` / `lstat /target: no such file or directory` durante `docker compose up --build`.
- Gli altri Dockerfile Spring erano già multi-stage.

## Docker network fix

The Docker Compose default network is now declared as an external shared network:

```yaml
networks:
  default:
    name: nebulaops-network
    external: true
```

This avoids collisions with a pre-existing `nebulaops-network` created outside Compose. The startup scripts create the network automatically when it is missing. For manual Compose runs, execute `./scripts/docker-network-fix.sh` once before `docker compose up -d --build`.

