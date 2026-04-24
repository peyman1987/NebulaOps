#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

command -v python3 >/dev/null || { echo "python3 is required"; exit 1; }
python3 scripts/validate-package.py
python3 scripts/validate-yaml.py .gitlab-ci.yml infrastructure/argocd/application.yaml infrastructure/argocd/project.yaml infrastructure/argocd/applicationset.yaml

echo "GitLab and Argo CD files are syntactically valid."
