#!/usr/bin/env bash
set -euo pipefail
SNAPSHOT="${1:-backups/latest}"
echo "Restore helper for $SNAPSHOT"
echo "Review files before overwriting live state. For MongoDB restore use mongorestore according to your volume strategy."
