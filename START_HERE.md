# Start Here — NebulaOps v16

1. Open WSL Ubuntu.
2. Enter the project directory.
3. Install the native toolchain if needed.
4. Start the platform.

```bash
cd nebulaops-v16
./scripts/wsl/install-native-toolchain.sh
./scripts/wsl/start.sh
```

Open http://localhost:4200 and use the 3D service map. Grafana is at http://localhost:3000 with `admin/admin`.

Troubleshooting Grafana:

```bash
docker compose -p nebulaops-v16 logs --tail=200 grafana
grep -R "isDefault: true" -n infrastructure/observability/grafana/provisioning/datasources
```

There must be exactly one default datasource.

## v16 functional 3D diagrams

The SVGs were rebuilt as technical flow diagrams. Start with `docs/V16_DIAGRAM_GUIDE.md`, then open:

- `docs/diagrams/request-flow-sequence.svg`
- `docs/diagrams/runtime-architecture.svg`
- `docs/diagrams/observability-grafana-flow.svg`
- `docs/diagrams/messaging-cache-flow.svg`
- `docs/diagrams/frontend-operations-dashboard.svg`

Each diagram uses cyan arrows for API control, purple dashed arrows for async events and green dotted arrows for
metrics/logs.
