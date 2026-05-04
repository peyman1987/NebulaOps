#!/usr/bin/env bash
# NebulaOps v23.3 — extension source verification for live-only runtime behavior.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"
log_step "Checking extension runtime shape"
required=(
  extensions/apiforge
  extensions/kubebridge
  extensions/contract-hub
)
for d in "${required[@]}"; do
  [ -f "$d/pom.xml" ] || { log_err "missing pom.xml: $d"; exit 1; }
  [ -f "$d/Dockerfile" ] || { log_err "missing Dockerfile: $d"; exit 1; }
done
log_step "Checking APIForge persistent files are empty at package start"
for f in extensions/apiforge/data/collections.json extensions/apiforge/data/environments.json extensions/apiforge/data/history.json; do
  python3 - "$f" <<'PY'
import json,sys
p=sys.argv[1]
with open(p,encoding='utf-8') as fh: data=json.load(fh)
if data != []:
    raise SystemExit(f"{p} must start empty; found persisted records")
PY
done
log_step "Checking external target wiring is provided by ConfigMap/Secret, not bundled credentials"
if grep -RInE 'jsonplaceholder|mockable|guest:guest|run-0094|eks-cluster|api\.example\.com|staging\.api\.example\.com|prod\.api\.example\.com' extensions extensions/*/k8s/deployment.yml 2>/dev/null; then
  log_err "Non-live seed endpoint, placeholder credential or legacy fixture detected"
  exit 1
fi
python3 - <<'PY'
import json, pathlib, sys
manifest = json.loads(pathlib.Path('extensions/extensions.manifest.json').read_text(encoding='utf-8'))
for item in manifest:
    if item.get('enabledByDefault') is not False or item.get('defaultState') != 'DISABLED':
        raise SystemExit(f"{item.get('slug')} must be disabled by default")
    k8s = pathlib.Path(item['kubernetesManifest']).read_text(encoding='utf-8')
    if 'replicas: 0' not in k8s:
        raise SystemExit(f"{item.get('slug')} Kubernetes manifest must use replicas: 0 by default")
PY
log_ok "Extension runtime verification completed"
