#!/usr/bin/env bash
# Ensures backend JARs are visible to Docker build context.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

cat > .dockerignore <<'DOCKERIGNORE'
.git
node_modules
**/node_modules
.angular
**/.angular
.cache
**/.cache
.idea
.vscode
*.log
.env
.m2
.build

# Frontend/MFE images have their own build contexts and .dockerignore files.
# Backend runtime images use the repository root as build context and must be
# able to COPY backend/<service>/target/*.jar. Do not exclude **/target here.
dist
**/dist
DOCKERIGNORE

log_ok "Docker build context repaired: backend target/*.jar files are visible to Docker"
