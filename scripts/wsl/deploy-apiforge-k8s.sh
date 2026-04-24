#!/usr/bin/env bash
# Backward-compatible wrapper for the APIForge-only deployment path.
set -euo pipefail
"$(dirname "${BASH_SOURCE[0]}")/deploy-extensions-k8s.sh" --only apiforge "$@"
