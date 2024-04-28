# Grafana fix: duplicate default datasource

Grafana fails with:

```text
Datasource provisioning error: datasource.yaml config is invalid. Only one datasource per organization can be marked as default
```

Cause: the provisioning folder contained two datasource YAML files that both declared Prometheus as `isDefault: true`.
Grafana reads every YAML file inside `/etc/grafana/provisioning/datasources`, so this creates two default datasources
for org `1`.

Fix applied:

- removed duplicate `prometheus.yml`
- kept a single datasource file: `datasource.yml`
- added a WSL start guard that stops startup if more than one `isDefault: true` is detected
- added Grafana health verification in the smoke test

Recovery command after applying this version:

```bash
./scripts/wsl/stop.sh
docker volume rm nebulaops-v15_grafana-data 2>/dev/null || true
./scripts/wsl/start.sh
curl -I http://localhost:3000
```

Expected response:

```text
HTTP/1.1 302 Found
```
