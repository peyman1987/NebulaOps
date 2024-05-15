# WSL Guide for NebulaOps v16

NebulaOps v16 does not require Docker Desktop. It installs Docker Engine directly inside WSL Ubuntu.

## Requirements

- Windows 11 with WSL2
- Ubuntu distribution
- 8 GB RAM minimum, 16 GB recommended
- Internet access during installation

## Install

```bash
./scripts/wsl/install-native-toolchain.sh
```

Then restart WSL from PowerShell:

```powershell
wsl --shutdown
```

Reopen Ubuntu and verify:

```bash
docker version
docker compose version
kubectl version --client=true
helm version
kind version
```

## Start NebulaOps

```bash
./scripts/linux/create-kind-cluster.sh nebulaops-v16
./scripts/linux/start-native.sh
```
