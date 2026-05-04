#!/usr/bin/env bash
# v23.3 — Docker build context guard for backend runtime images.
#
# Backend service Dockerfiles use the repository root as build context and copy:
#   backend/<service>/target/*.jar
# This script makes the check explicit and prevents regressions where .dockerignore
# accidentally excludes target directories/JARs from the Docker build context.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

cd "$ROOT_DIR"

log_step "Checking Docker build context for backend JAR visibility"

mkdir -p reports

DOCKERIGNORE="$ROOT_DIR/.dockerignore"
if [ ! -f "$DOCKERIGNORE" ]; then
  cat > "$DOCKERIGNORE" <<'IGNORE'
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

dist
**/dist
IGNORE
  log_warn "Created missing root .dockerignore"
fi

# Remove broad target exclusions from the root .dockerignore. Backend Dockerfiles
# need target/*.jar visible because root docker-compose builds with context '.'.
if awk '!/^([[:space:]]*)#/ && !/^([[:space:]]*)$/ { print }' "$DOCKERIGNORE" | grep -Eq '(^|/|\*)target(/|$)|\*\*/target|target/|target$'; then
  tmp_file="$(mktemp)"
  awk '
    /^([[:space:]]*)#/ { print; next }
    /^([[:space:]]*)$/ { print; next }
    $0 ~ /(^|\/|\*)target(\/|$)/ { next }
    $0 ~ /\*\*\/target/ { next }
    $0 == "target" { next }
    $0 == "target/" { next }
    { print }
  ' "$DOCKERIGNORE" > "$tmp_file"
  mv "$tmp_file" "$DOCKERIGNORE"
  log_warn "Removed target exclusions from root .dockerignore"
fi

# Keep the explanatory comment present, without duplicating it on every run.
if ! grep -q "Backend runtime images use the repository root as build context" "$DOCKERIGNORE"; then
  cat >> "$DOCKERIGNORE" <<'IGNORE'

# Frontend/MFE images have their own build contexts and .dockerignore files.
# Backend runtime images use the repository root as build context and must be
# able to COPY backend/<service>/target/*.jar. Do not exclude **/target here.
IGNORE
fi

services=(
  auth-service
  task-service
  notification-service
  file-service
  ai-ops-service
  devsecops-service
  pipeline-engine-service
  observability-service
  gitops-control-service
  environment-manager-service
  terraform-studio-service
  gateway-service
  cost-analytics-service
  release-orchestrator-service
  policy-governance-service
  progressive-delivery-service
  audit-service
  spring-mvc-service
)

missing=0
json_items=()
for service in "${services[@]}"; do
  jar_dir="backend/${service}/target"
  mapfile -t jars < <(find "$jar_dir" -maxdepth 1 -type f -name '*.jar' ! -name '*.jar.original' 2>/dev/null | sort || true)
  if [ "${#jars[@]}" -eq 0 ]; then
    log_warn "Docker context check: missing JAR for ${service}. Build backend before Docker image build."
    missing=1
    json_items+=("{\"service\":\"${service}\",\"status\":\"MISSING_JAR\",\"jar\":null}")
  elif [ "${#jars[@]}" -gt 1 ]; then
    log_warn "Docker context check: ${service} has multiple JARs; Docker COPY may be ambiguous"
    json_items+=("{\"service\":\"${service}\",\"status\":\"MULTIPLE_JARS\",\"jar\":\"${jars[0]}\"}")
  else
    log_ok "Docker context check: ${service} JAR visible"
    json_items+=("{\"service\":\"${service}\",\"status\":\"OK\",\"jar\":\"${jars[0]}\"}")
  fi
done

{
  printf '{\n'
  printf '  "check": "docker-build-context",\n'
  printf '  "rootDockerignore": "%s",\n' ".dockerignore"
  printf '  "status": "%s",\n' "$([ "$missing" -eq 0 ] && printf OK || printf MISSING_JARS)"
  printf '  "services": [\n'
  for i in "${!json_items[@]}"; do
    printf '    %s' "${json_items[$i]}"
    if [ "$i" -lt "$((${#json_items[@]} - 1))" ]; then printf ','; fi
    printf '\n'
  done
  printf '  ]\n'
  printf '}\n'
} > reports/docker-context.json

if [ "$missing" -ne 0 ]; then
  log_err "Docker context check failed: one or more backend JARs are missing. Run ./scripts/wsl/build-backend-jars.sh or ./scripts/wsl/start.sh --rebuild."
  exit 1
fi

log_ok "Docker build context is ready for backend runtime image builds"
