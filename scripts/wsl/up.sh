#!/usr/bin/env bash
# Short alias for the NebulaOps v22.4 one-command launcher.
set -euo pipefail
"$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/run-v22.4.sh" "$@"
