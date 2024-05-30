# Validation Report v17

## Static checks performed

- Project unpacked from v14 ZIP and copied to `nebulaops-v17`.
- Version references updated to v17 in primary files.
- Gateway runtime controller added for Docker, Helm and Grafana.
- Frontend tabs added for Docker, Helm and Grafana.
- Native WSL/Linux scripts added for Docker Engine, Docker Compose plugin, kubectl, Helm and kind.
- Architecture, runbook, feature matrix and animated SVG added.

## Environment limitation

The sandbox used to generate this package does not include Maven or Node package installation, so full compile/build
execution could not be completed here. The included scripts are designed to install the required native toolchain on
WSL/Linux and then run the project locally.

## Recommended local validation

```bash
./scripts/wsl/check-wsl.sh
docker compose config
docker compose up -d --build
./scripts/smoke-test.sh
./scripts/linux/create-kind-cluster.sh nebulaops-v17
./scripts/linux/helm-install-nebulaops.sh nebulaops
```
