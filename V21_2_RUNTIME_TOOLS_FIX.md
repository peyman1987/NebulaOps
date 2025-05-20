# NebulaOps v21.2 Runtime Tools Fix

Fixes `kubectl: not found` and similar tool discovery errors inside backend containers.

Changes:

- Added `scripts/wsl/prepare-runtime-tools.sh`.
- Mounted `.runtime-tools` into live backend containers.
- Prepended `/opt/nebula-tools` to backend PATH.
- Updated start scripts and docs.
- Preserved live-only behavior: no static fallback data.
