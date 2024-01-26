#!/usr/bin/env bash
set -euo pipefail
if ! command -v apt-get >/dev/null 2>&1; then echo "This installer targets Ubuntu/Debian in WSL."; exit 1; fi
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg lsb-release jq unzip git
cat <<'INFO'
Docker recommended setup:
1) Install Docker Desktop on Windows
2) Enable Settings > Resources > WSL Integration > your Ubuntu distro
3) Restart terminal and run ./scripts/wsl/check-wsl.sh
INFO
