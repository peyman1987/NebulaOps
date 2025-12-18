# Start here

Use the v22.3 same-origin runtime.

```bash
cd nebulaops-v22.3
chmod +x scripts/wsl/*.sh scripts/*.sh
./scripts/wsl/start.sh --rebuild
./scripts/wsl/health.sh
```

Open the shell at:

```text
http://nebulaops.localhost
```

The shell and every micro frontend use the same origin. Standalone MFE buttons open `/remotes/<mfe>/`, not legacy public MFE 42xx ports.

Core checks:

```bash
curl -I http://nebulaops.localhost/
curl -I http://nebulaops.localhost/remotes/docker-desktop/remoteEntry.js
curl -I http://nebulaops.localhost/remotes/openlens-kubernetes/remoteEntry.js
curl -I http://nebulaops.localhost/actuator/health
```
