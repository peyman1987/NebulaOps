#!/usr/bin/env bash
# v24.1 — Convenience launcher for Keycloak-protected Mongo/RabbitMQ/Redis tool UIs.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/start.sh" --with-sso-proxy "$@"
