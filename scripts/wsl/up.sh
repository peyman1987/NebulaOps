#!/usr/bin/env bash
# Short alias for the NebulaOps v24.1 one-command launcher.
set -euo pipefail
"$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/run-v24.1.sh" "$@"
