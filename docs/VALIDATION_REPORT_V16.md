# NebulaOps v19.3 Validation Report

Generated package: `nebulaops-v19-3.zip`.

## Static checks completed in the build environment

- Project was upgraded from the v15 package structure to `nebulaops-v19-3`.
- v15/v17 naming was normalized across scripts, compose and documentation.
- Grafana provisioning was corrected to keep exactly one default datasource.
- Duplicate Grafana datasource provisioning files were removed.
- Duplicate dashboard provider files were removed.
- WSL start script now validates the Grafana datasource default count before starting containers.
- New Angular 3D SaaS-style UI files were generated.
- New v17 architecture and feature matrix documents were added.
- New animated SVG architecture diagram was added.

## Checks to run locally

```bash
./scripts/wsl/start.sh
./scripts/wsl/smoke-test.sh
docker compose -p nebulaops-v19-3 ps
docker compose -p nebulaops-v19-3 logs --tail=120 grafana
curl -I http://localhost:3000
```

Expected Grafana result:

```text
HTTP/1.1 302 Found
```

## Limitation

The package creation environment did not have Docker available, so full container startup was not executed here. Run the
WSL commands above on your machine.
