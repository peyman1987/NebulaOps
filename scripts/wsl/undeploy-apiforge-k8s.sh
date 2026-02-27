#!/usr/bin/env bash
# Backward-compatible wrapper for removing APIForge only.
set -euo pipefail
"$(dirname "${BASH_SOURCE[0]}")/undeploy-extensions-k8s.sh" --only apiforge "$@"
