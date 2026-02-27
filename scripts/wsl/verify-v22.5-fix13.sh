#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
fail(){ echo "✗ $*" >&2; exit 1; }
ok(){ echo "✓ $*"; }

grep -q "ANTHROPIC_API_KEY" ai-engine/app/main.py || fail "AI Engine does not use ANTHROPIC_API_KEY"
grep -q "https://api.anthropic.com/v1/messages" ai-engine/app/main.py || fail "AI Engine Anthropic endpoint missing"
grep -q 'version", VERSION' backend/gateway-service/src/main/java/dev/nebulaops/gateway/api/HealthController.java || fail "Gateway HealthController version constant missing"
grep -q "RestTemplate rest" backend/ai-ops-service/src/main/java/dev/nebulaops/aiops/AiOpsController.java || fail "AiOpsController does not inject RestTemplate"
! grep -R "RestTemplateBuilder.*connectTimeout\|RestTemplateBuilder.*readTimeout\|\.connectTimeout(Duration\|\.readTimeout(Duration" backend/ai-ops-service/src/main/java >/dev/null || fail "ai-ops-service uses Spring Boot 3.4-only RestTemplateBuilder timeout API"
grep -q "analyzeThroughEngine" backend/ai-ops-service/src/main/java/dev/nebulaops/aiops/AiOpsController.java || fail "AiOpsController dual-path consolidation missing"
for svc in ai-ops-service auth-service gateway-service; do
  grep -q "spring-boot-starter-test" "backend/$svc/pom.xml" || fail "$svc missing starter-test"
  test -d "backend/$svc/src/test/java" || fail "$svc missing test suite"
done
grep -q "mockito-junit-jupiter" backend/ai-ops-service/pom.xml || fail "ai-ops-service missing mockito junit"
for f in backend/nebulaops-shared-kernel/src/main/java/dev/nebulaops/shared/extensions/AbstractExtensionController.java backend/nebulaops-shared-kernel/src/main/java/dev/nebulaops/shared/http/NebulaHttpClient.java; do
  test -f "$f" || fail "shared kernel file missing: $f"
done
grep -q "ObservabilityCache" go/cache-service/internal/cache/observability.go || fail "Go ObservabilityCache missing"
grep -q "ObservabilityCacheClient" backend/gateway-service/src/main/java/dev/nebulaops/gateway/service/ObservabilityCacheClient.java || fail "gateway ObservabilityCacheClient missing"
grep -q "APIFORGE_DATA_DIR" extensions/apiforge/k8s/deployment.yml || fail "APIForge data env missing"
grep -q "/var/lib/apiforge" extensions/apiforge/k8s/deployment.yml || fail "APIForge data dir not pointed to PVC mount"
grep -q "Trivy" .github/workflows/ci.yml || fail "GitHub Trivy scan missing"
grep -q "security:trivy-fs" .gitlab-ci.yml || fail "GitLab Trivy stage missing"
grep -q "ANTHROPIC_API_KEY" .env.example || fail ".env.example missing ANTHROPIC_API_KEY"
grep -R "NebulaOps v22.5 SECURITY WARNING" backend/*/src/main/resources/application.yml >/dev/null || fail "security warnings missing in application.yml"
grep -q "class NebulaApiClient" frontend/src/app/api.config.ts || fail "typed NebulaApiClient missing"
python3 -m py_compile ai-engine/app/main.py
(cd go/cache-service && timeout 90s go test ./...)
ok "v22.5 Fix 1-13 guard passed"
