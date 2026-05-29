# Start here

Use the v24.1 same-origin runtime.

```bash
cd nebulaops-v24.1
chmod +x scripts/wsl/*.sh scripts/*.sh
./scripts/wsl/start.sh --rebuild
./scripts/wsl/health.sh
```


Fast development startup:

```bash
./scripts/wsl/start.sh --core
```

Full runtime startup:

```bash
./scripts/wsl/start.sh --full
```

Selective frontend rebuild:

```bash
./scripts/wsl/build-frontend-changed.sh
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
python3 scripts/validate-version-alignment-v24.1.py
./scripts/wsl/smoke-version-alignment-v24.1.sh
```


## v24.1 compile guard

Before a full runtime rebuild, use the dedicated compile pipeline:

```bash
./scripts/wsl/verify-compile-readiness-v24.1.sh
./scripts/wsl/compile-v24.1.sh --force
```

For UI-only validation:

```bash
./scripts/wsl/compile-v24.1.sh --frontend-only --force
```

For backend-only validation after Java/API changes:

```bash
./scripts/wsl/compile-v24.1.sh --backend-only --force
```

The backend builder uses host Maven when available and falls back to Dockerized Maven/JDK 21 when Docker Desktop is reachable.
