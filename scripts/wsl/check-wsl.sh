#!/usr/bin/env bash
set -euo pipefail
ok(){ echo "OK $1"; }
warn(){ echo "WARN $1"; }
fail(){ echo "FAIL $1"; exit 1; }
echo "NebulaOps v23.1 native WSL pre-flight check"
grep -qi microsoft /proc/version && ok "Running inside WSL" || warn "Not detected as WSL"
command -v docker >/dev/null 2>&1 && ok "docker CLI found" || fail "docker CLI missing. Run ./scripts/wsl/install-native-toolchain.sh"
docker info >/dev/null 2>&1 && ok "Native Docker daemon reachable" || fail "Docker daemon is not reachable. Start it with: sudo service docker start"
docker compose version >/dev/null 2>&1 && ok "docker compose plugin found" || fail "docker compose plugin missing"
command -v kubectl >/dev/null 2>&1 && ok "kubectl found" || fail "kubectl missing"
command -v helm >/dev/null 2>&1 && ok "helm found" || fail "helm missing"
command -v kind >/dev/null 2>&1 && ok "kind found" || warn "kind missing; install native toolchain if Kubernetes local cluster is needed"
[[ -S /var/run/docker.sock ]] && ok "Docker socket exists" || fail "Docker socket missing"
