# NebulaOps v22.2 — Validation Report

## Static validation

- YAML parse: OK
- Bash syntax: OK
- Remote entries: OK — each remote registers a custom element.
- Docker Compose contains four FE containers: shell + three remotes.

## Runtime validation to execute in WSL

```bash
./scripts/wsl/stop.sh
./scripts/wsl/start.sh
./scripts/wsl/health.sh
```

Expected FE endpoints:

- Shell: http://localhost:4200
- Docker/OpenLens MFE: http://localhost:4211/remoteEntry.js
- Task Management MFE: http://localhost:4212/remoteEntry.js
- Platform Core MFE: http://localhost:4213/remoteEntry.js
