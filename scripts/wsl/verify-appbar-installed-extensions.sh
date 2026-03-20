#!/usr/bin/env bash
# NebulaOps v23.1 — verify APP BAR and EXTENSIONS are split cleanly.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

log_step "Verifying APP BAR / EXTENSIONS split"

expected="apiforge contract-hub kubebridge "
installed_dirs="$(find extensions -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort | tr '\n' ' ')"
if [ "$installed_dirs" != "$expected" ]; then
  log_err "Installed extension directories must be: $expected. Found: ${installed_dirs:-none}"
  exit 1
fi

python3 - <<'PY'
import json, pathlib, sys
expected = ['apiforge', 'kubebridge', 'contract-hub']
manifest = pathlib.Path('extensions/extensions.manifest.json')
items = json.loads(manifest.read_text(encoding='utf-8'))
slugs = [item.get('slug') for item in items]
if slugs != expected:
    print(f"extensions.manifest.json must expose {expected}; found {slugs}", file=sys.stderr)
    sys.exit(1)
for item in items:
    actions = item.get('actions') or []
    missing = [a for a in ['start','stop','restart','status','open'] if a not in actions]
    if missing:
        print(f"{item.get('slug')} missing actions: {missing}", file=sys.stderr)
        sys.exit(1)
app = pathlib.Path('frontend/src/app/app.component.ts').read_text(encoding='utf-8')
if '"group": "Extensions"' in app or "group: 'Extensions'" in app:
    print('APP BAR serviceLinks must not contain Extensions cards; extensions are controlled by the separate EXTENSIONS panel.', file=sys.stderr)
    sys.exit(1)
html = pathlib.Path('frontend/src/app/app.component.html').read_text(encoding='utf-8')
if 'neb-extensions-trigger' not in html:
    print('Sidebar EXTENSIONS trigger is missing from Angular template.', file=sys.stderr)
    sys.exit(1)
asset = pathlib.Path('frontend/src/assets/nebulaops-extension-control-panel.js').read_text(encoding='utf-8')
required = {
    'apiforge': '/api/extensions/apiforge/status',
    'kubebridge': '/api/extensions/kubebridge/status',
    'contract-hub': '/api/extensions/contract-hub/status',
}
# The script builds URLs dynamically, so validate slugs/action vocabulary instead of exact literals.
for slug in required:
    if slug not in asset:
        print(f'Extension control panel missing slug: {slug}', file=sys.stderr)
        sys.exit(1)
for action in ['start','stop','restart','status','open']:
    if action not in asset:
        print(f'Extension control panel missing action: {action}', file=sys.stderr)
        sys.exit(1)
index = pathlib.Path('frontend/dist/nebulaops/browser/index.html').read_text(encoding='utf-8')
if 'nebulaops-extension-appbar-controls.js' in index:
    print('Legacy nested APP BAR extension controls script is still loaded in dist index.', file=sys.stderr)
    sys.exit(1)
if 'nebulaops-extension-control-panel.js' not in index:
    print('New EXTENSIONS control panel script is not loaded in dist index.', file=sys.stderr)
    sys.exit(1)
PY

for forbidden in "Runbook Center" "Extension Registry" "EventOps Center" "Observability Lens" "GitOps Center" "Secrets & Config Center" "SLO Center" "Backup & Recovery Center"; do
  if grep -R --fixed-strings "$forbidden" frontend/src/app frontend/dist/nebulaops/browser/main-*.js >/dev/null 2>&1; then
    log_err "Removed extension still appears in APP BAR/frontend shell artifact: $forbidden"
    exit 1
  fi
done

log_ok "APP BAR contains platform services only; EXTENSIONS panel controls APIForge, KubeBridge and Contract Hub"
