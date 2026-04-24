#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "$SCRIPT_DIR/../linux/install-native-toolchain.sh"
if ! grep -q '^\[boot\]' /etc/wsl.conf 2>/dev/null; then
  sudo tee -a /etc/wsl.conf >/dev/null <<'CONF'
[boot]
systemd=true
CONF
fi
echo "WSL configured for native Docker Engine. Run from PowerShell: wsl --shutdown, then reopen Ubuntu."
