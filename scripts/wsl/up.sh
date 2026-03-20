#!/usr/bin/env bash
# Short alias for the NebulaOps v23.1 one-command launcher.
set -euo pipefail
"$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/run-v23.1.sh" "$@"
