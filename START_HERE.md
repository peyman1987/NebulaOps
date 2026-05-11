# Start here

Use the v23.4 same-origin runtime.

```bash
cd nebulaops-v23.4
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

Release identity checks:

```bash
python3 scripts/validate-version-alignment-v23.4.py
./scripts/wsl/smoke-version-alignment-v23.4.sh
```
