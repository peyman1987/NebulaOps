#!/usr/bin/env bash
# Diagnose and optionally print a safe JAVA_HOME export for WSL.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

log_step "Java/JDK diagnostics"
printf 'java:  '; command -v java || true
printf 'javac: '; command -v javac || true
printf 'mvn:   '; command -v mvn || true
printf 'JAVA_HOME=%s\n' "${JAVA_HOME:-<unset>}"

if command -v javac >/dev/null 2>&1; then
  javac_path="$(readlink -f "$(command -v javac)" 2>/dev/null || command -v javac)"
  detected_home="$(dirname "$(dirname "$javac_path")")"
  if [ -x "$detected_home/bin/java" ] && [ -x "$detected_home/bin/javac" ]; then
    log_ok "Detected JDK: $detected_home"
    cat <<EOF

To use host Maven explicitly, run:
  export JAVA_HOME="$detected_home"
  export PATH="\$JAVA_HOME/bin:\$PATH"
  ./scripts/wsl/build-backend-jars.sh --host-maven --force

For normal NebulaOps startup this is optional; start.sh can use Dockerized Maven automatically.
EOF
    exit 0
  fi
fi

log_warn "No usable JDK detected on the host."
cat <<'EOF'

Recommended install for Ubuntu/WSL:
  sudo apt-get update
  sudo apt-get install -y openjdk-21-jdk maven

Alternative without host JDK:
  ./scripts/wsl/build-backend-jars.sh --docker-maven --force
EOF
