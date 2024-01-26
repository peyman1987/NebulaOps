#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

python3 scripts/validate-package.py
python3 scripts/validate-yaml.py
find scripts -name "*.sh" -print0 | xargs -0 -I{} bash -n {}

if command -v go >/dev/null 2>&1; then
  (cd go/cache-service && go test ./...)
  (cd go/event-worker && go test ./...)
else
  echo "go not found: skipping Go tests"
fi

if command -v npm >/dev/null 2>&1; then
  (
    cd frontend
    if [ ! -d node_modules ]; then
      if [ -f package-lock.json ]; then npm ci --silent; else npm install --silent; fi
    fi
    npm run build --silent
  )
else
  echo "npm not found: skipping frontend build"
fi

if command -v mvn >/dev/null 2>&1; then
  mvn -q -f backend/pom.xml test
else
  echo "mvn not found: skipping Java Maven tests"
fi

echo "NebulaOps validation completed"
