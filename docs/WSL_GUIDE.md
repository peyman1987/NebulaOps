# WSL guide

## Start

```bash
cd /mnt/d/workspace/personal/NebulaOps/nebulaops-v23.3
chmod +x scripts/wsl/*.sh scripts/*.sh
./scripts/wsl/start.sh --rebuild
```

Open:

```text
http://nebulaops.localhost
```

## Health

```bash
./scripts/wsl/health.sh
```

## Stop

```bash
./scripts/wsl/stop.sh
```

## Notes

Do not open old public MFE URLs. Standalone MFE pages are served by the frontend container under `/remotes/<mfe>/`.
