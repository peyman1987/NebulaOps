#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"
log_step "Regenerating live-only remoteEntry bundles"
for remote_dir in frontend/remotes/*; do
  [ -d "$remote_dir" ] || continue
  [ -f "$remote_dir/live-endpoints.json" ] || continue
  node frontend/tools/generate-live-only-remote.cjs "$remote_dir"
done
log_step "Verifying live-only MFE bundles"
./scripts/wsl/verify-v22.5-real-data-mode.sh
log_step "Ensuring backend JARs for live-only endpoint policy"
"$ROOT_DIR/scripts/wsl/build-backend-jars.sh"
log_step "Rebuilding backend images affected by live-only endpoint policy"
dc build gateway-service cost-analytics-service policy-governance-service
dc up -d --force-recreate gateway-service cost-analytics-service policy-governance-service
log_step "Rebuilding frontend and MFE runtime images"
./scripts/wsl/repair-v22.5-frontend-remotes.sh
cat <<'MSG'

Live-only data mode is active.
Open a fresh browser session:
  http://nebulaops.localhost/?v=v22.5.6-live-real-data
Use Ctrl+Shift+R. If Chrome has old localhost resources, clear site data for localhost.
MSG
