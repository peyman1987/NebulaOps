#!/usr/bin/env bash
set -euo pipefail
ok(){ printf "\033[32mOK\033[0m %s\n" "$1"; }
warn(){ printf "\033[33mWARN\033[0m %s\n" "$1"; }
fail(){ printf "\033[31mFAIL\033[0m %s\n" "$1"; exit 1; }
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
echo "NebulaOps WSL pre-flight check"
echo "Project: $ROOT_DIR"
if grep -qi microsoft /proc/version 2>/dev/null; then ok "Running inside WSL"; else warn "This does not look like WSL. Scripts still work on Linux."; fi
if [[ "$ROOT_DIR" == /mnt/* ]]; then warn "Project is under /mnt/*. For better Docker/Node performance, copy it into ~/projects/nebulaops."; else ok "Project is on Linux filesystem"; fi
command -v docker >/dev/null 2>&1 && ok "docker CLI found" || fail "docker CLI missing. Install Docker Desktop and enable WSL integration."
docker compose version >/dev/null 2>&1 && ok "docker compose plugin found" || fail "docker compose plugin missing"
docker info >/dev/null 2>&1 && ok "Docker daemon reachable" || fail "Docker daemon is not reachable. Start Docker Desktop and enable WSL Integration."
command -v curl >/dev/null 2>&1 && ok "curl found" || warn "curl missing"
command -v jq >/dev/null 2>&1 && ok "jq found" || warn "jq missing; optional"
mem_kb=$(grep MemTotal /proc/meminfo | awk '{print $2}')
mem_gb=$((mem_kb/1024/1024))
if (( mem_gb >= 6 )); then ok "WSL memory: ${mem_gb}GB"; else warn "WSL memory: ${mem_gb}GB. Recommended 6GB+."; fi
cpu_count=$(nproc || echo 1)
if (( cpu_count >= 4 )); then ok "CPU cores: ${cpu_count}"; else warn "CPU cores: ${cpu_count}. Recommended 4+."; fi
echo "Next: ./scripts/wsl/start.sh"
