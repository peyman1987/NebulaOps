#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="$ROOT_DIR/.kube"
OUT_FILE="$OUT_DIR/config"
mkdir -p "$OUT_DIR"

SOURCE="${KUBECONFIG:-}"
if [[ -z "$SOURCE" || ! -f "$SOURCE" ]]; then
  if [[ -f /etc/rancher/k3s/k3s.yaml ]]; then
    SOURCE=/etc/rancher/k3s/k3s.yaml
  elif [[ -f "$HOME/.kube/config" ]]; then
    SOURCE="$HOME/.kube/config"
  else
    echo "ERROR: kubeconfig not found. Expected /etc/rancher/k3s/k3s.yaml or ~/.kube/config" >&2
    exit 1
  fi
fi

if [[ ! -r "$SOURCE" ]]; then
  sudo cp "$SOURCE" "$OUT_FILE"
  sudo chown "$USER:$USER" "$OUT_FILE"
else
  cp "$SOURCE" "$OUT_FILE"
fi
chmod 600 "$OUT_FILE"

WSL_IP="$(hostname -I | awk '{print $1}')"
if [[ -z "$WSL_IP" ]]; then
  echo "ERROR: unable to detect WSL IP" >&2
  exit 1
fi

python3 - "$OUT_FILE" "$WSL_IP" <<'PY'
from pathlib import Path
import re, sys
path = Path(sys.argv[1])
ip = sys.argv[2]
text = path.read_text()
text = re.sub(r'server:\s*https?://(127\.0\.0\.1|localhost)(:\d+)', f'server: https://{ip}\\2', text)
text = re.sub(r'server:\s*http://(127\.0\.0\.1|localhost):8080', f'server: https://{ip}:6443', text)
path.write_text(text)
PY

echo "Generated Docker kubeconfig: $OUT_FILE"
grep -n "server:" "$OUT_FILE" || true
