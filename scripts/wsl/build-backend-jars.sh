#!/usr/bin/env bash
# NebulaOps v24.1 reliable backend build.
# Builds all Spring Boot service JARs once with a shared Maven repository, so
# Docker image builds do not redownload Maven dependencies per microservice.
#
# Important for WSL: host Maven is used only when it is actually runnable.
# If JAVA_HOME is missing/wrong, the script automatically falls back to a
# Dockerized Maven + JDK 21 builder, avoiding the classic:
#   "The JAVA_HOME environment variable is not defined correctly".
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"
cd "$ROOT_DIR"

CLEAN=false
FORCE=false
PREFER_DOCKER_MAVEN="${NEBULAOPS_FORCE_DOCKER_MAVEN:-false}"
PREFER_HOST_MAVEN="${NEBULAOPS_FORCE_HOST_MAVEN:-false}"
MAX_RETRIES="${NEBULAOPS_MAVEN_RETRIES:-3}"
for arg in "$@"; do
  case "$arg" in
    --clean) CLEAN=true ;;
    --force) FORCE=true ;;
    --docker-maven) PREFER_DOCKER_MAVEN=true ;;
    --host-maven) PREFER_HOST_MAVEN=true ;;
    -h|--help)
      cat <<'USAGE'
Usage: ./scripts/wsl/build-backend-jars.sh [--clean] [--force] [--docker-maven] [--host-maven]

Builds all backend Spring Boot JARs using a persistent Maven cache in .m2/.

Default behavior:
  - use host Maven only if `mvn -version` succeeds with a valid JDK;
  - automatically fall back to Docker Maven when host JAVA_HOME is invalid;
  - retry transient Maven Central/network failures without deleting .m2/.

Options:
  --clean         remove backend target/ directories before building
  --force         build even if all target/*.jar files already exist
  --docker-maven  force Dockerized Maven + JDK 21 builder
  --host-maven    force host Maven; fails fast if JAVA_HOME/JDK is invalid
USAGE
      exit 0
      ;;
  esac
done

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

need_build=false
for service in "${services[@]}"; do
  if ! compgen -G "backend/${service}/target/*.jar" >/dev/null; then
    need_build=true
    break
  fi
done

if [ "$FORCE" != "true" ] && [ "$need_build" != "true" ]; then
  log_ok "Backend JARs already present; skipping Maven build"
  exit 0
fi

mkdir -p "$ROOT_DIR/.m2/repository" "$ROOT_DIR/.build/logs"

if [ "$CLEAN" = "true" ]; then
  log_step "Cleaning backend target directories"
  find backend -maxdepth 2 -type d -name target -prune -exec rm -rf {} +
fi

maven_args=(
  -B
  -ntp
  -f backend/pom.xml
  -Dmaven.repo.local=/workspace/.m2/repository
  -DskipTests
  -Dmaven.test.skip=true
  -DskipITs
  -Dmaven.wagon.http.retryHandler.count=8
  -Dmaven.wagon.httpconnectionManager.ttlSeconds=300
  -Dmaven.wagon.rto=600000
  -Dmaven.wagon.http.pool=false
  clean package
)

candidate_java_homes() {
  cat <<'CANDIDATES'
/usr/lib/jvm/java-21-openjdk-amd64
/usr/lib/jvm/java-21-openjdk
/usr/lib/jvm/msopenjdk-21-amd64
/usr/lib/jvm/temurin-21-jdk-amd64
/usr/lib/jvm/java-17-openjdk-amd64
/usr/lib/jvm/java-17-openjdk
/usr/lib/jvm/msopenjdk-17-amd64
/usr/lib/jvm/default-java
CANDIDATES
}

java_home_has_jdk() {
  local home="$1"
  [ -n "$home" ] && [ -x "$home/bin/java" ] && [ -x "$home/bin/javac" ]
}

try_auto_java_home() {
  # Existing JAVA_HOME is valid: keep it.
  if java_home_has_jdk "${JAVA_HOME:-}"; then
    export PATH="$JAVA_HOME/bin:$PATH"
    return 0
  fi

  # Derive JAVA_HOME from javac when available.
  if command -v javac >/dev/null 2>&1; then
    local javac_path java_home
    javac_path="$(readlink -f "$(command -v javac)" 2>/dev/null || command -v javac)"
    java_home="$(dirname "$(dirname "$javac_path")")"
    if java_home_has_jdk "$java_home"; then
      export JAVA_HOME="$java_home"
      export PATH="$JAVA_HOME/bin:$PATH"
      log_ok "JAVA_HOME detected from javac: $JAVA_HOME"
      return 0
    fi
  fi

  # Try common Linux/WSL JDK locations.
  local candidate
  while IFS= read -r candidate; do
    if java_home_has_jdk "$candidate"; then
      export JAVA_HOME="$candidate"
      export PATH="$JAVA_HOME/bin:$PATH"
      log_ok "JAVA_HOME detected: $JAVA_HOME"
      return 0
    fi
  done < <(candidate_java_homes)

  return 1
}

host_maven_is_usable() {
  [ "$PREFER_DOCKER_MAVEN" = "true" ] && return 1
  command -v mvn >/dev/null 2>&1 || return 1
  try_auto_java_home || true
  mvn -version >/dev/null 2>&1
}

run_maven_host() {
  local host_args=()
  local arg
  for arg in "${maven_args[@]}"; do
    host_args+=("${arg//\/workspace/$ROOT_DIR}")
  done
  log_info "Using host Maven with JAVA_HOME=${JAVA_HOME:-<unset>}"
  mvn "${host_args[@]}"
}

run_maven_docker() {
  if ! docker info >/dev/null 2>&1; then
    log_err "Docker daemon is not reachable, and host Maven/JDK is not usable."
    log_err "Install JDK 21 in WSL or start Docker Desktop, then rerun this script."
    log_err "For JDK 21 on Ubuntu/WSL: sudo apt-get update && sudo apt-get install -y openjdk-21-jdk maven"
    return 1
  fi
  log_warn "Host Maven/JDK is not usable; using Dockerized Maven + JDK 21 builder"
  log_info "This avoids invalid JAVA_HOME problems and keeps dependencies in .m2/repository"
  docker run --rm \
    --user "$(id -u):$(id -g)" \
    -v "$ROOT_DIR:/workspace" \
    -w /workspace \
    -e MAVEN_CONFIG=/workspace/.m2 \
    -e MAVEN_OPTS="-Djava.net.preferIPv4Stack=true -Xmx1600m" \
    maven:3.9.9-eclipse-temurin-21 \
    mvn "${maven_args[@]}"
}

if [ "$PREFER_HOST_MAVEN" = "true" ] && ! host_maven_is_usable; then
  log_err "--host-maven was requested, but host Maven/JDK is not usable."
  log_err "Current JAVA_HOME=${JAVA_HOME:-<unset>}"
  log_err "Install/configure a JDK, not only a JRE. Example: sudo apt-get install -y openjdk-21-jdk maven"
  exit 1
fi

attempt=1
while [ "$attempt" -le "$MAX_RETRIES" ]; do
  log_step "Building backend JARs with shared Maven cache (attempt ${attempt}/${MAX_RETRIES})"
  set +e
  if host_maven_is_usable; then
    run_maven_host 2>&1 | tee "$ROOT_DIR/.build/logs/backend-maven-attempt-${attempt}.log"
    status=${PIPESTATUS[0]}
  else
    run_maven_docker 2>&1 | tee "$ROOT_DIR/.build/logs/backend-maven-attempt-${attempt}.log"
    status=${PIPESTATUS[0]}
  fi
  set -e
  if [ "$status" -eq 0 ]; then
    break
  fi
  if [ "$attempt" -eq "$MAX_RETRIES" ]; then
    log_err "Backend Maven build failed after ${MAX_RETRIES} attempts"
    log_err "Inspect: .build/logs/backend-maven-attempt-${attempt}.log"
    exit "$status"
  fi
  log_warn "Maven build failed, retrying. Already downloaded artifacts stay in .m2/repository"
  attempt=$((attempt + 1))
  sleep 5
done

missing=0
for service in "${services[@]}"; do
  if compgen -G "backend/${service}/target/*.jar" >/dev/null; then
    jar_count=$(find "backend/${service}/target" -maxdepth 1 -name '*.jar' ! -name '*.jar.original' | wc -l | tr -d ' ')
    if [ "$jar_count" -eq 1 ]; then
      log_ok "${service} JAR ready"
    else
      log_warn "${service} has ${jar_count} executable-looking JARs; Docker COPY may be ambiguous"
    fi
  else
    log_err "Missing JAR: backend/${service}/target/*.jar"
    missing=1
  fi
done

[ "$missing" -eq 0 ] || exit 1

# Docker image builds use the repository root as context and copy
# backend/<service>/target/*.jar. A root .dockerignore that excludes **/target
# without re-including those JARs makes Docker fail with:
#   lstat /backend/<service>/target: no such file or directory
if [ -f "$ROOT_DIR/scripts/wsl/repair-v24.1-docker-context.sh" ]; then
  "$ROOT_DIR/scripts/wsl/repair-v24.1-docker-context.sh" >/dev/null
fi

log_ok "Backend build completed"
