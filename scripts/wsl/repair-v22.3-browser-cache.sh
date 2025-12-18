#!/usr/bin/env bash
# Browser-cache safe restart for NebulaOps v22.3 frontend/MFE remotes.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

log_step "Rebuilding frontend and MFE images with no-cache headers and versioned remoteEntry URLs"
"$ROOT_DIR/scripts/wsl/repair-v22.3-frontend-remotes.sh"

cat <<'EOF'

Browser-side cleanup:
  1. Open http://nebulaops.localhost/?v=v22.3.6-live-real-data
  2. Press Ctrl+Shift+R once.
  3. If an old tab still shows blank content, open Chrome DevTools > Application > Storage > Clear site data for localhost.

The package now also sends Cache-Control: no-store for index.html and remoteEntry.js, and the shell appends a version query to every remoteEntry request.
EOF
