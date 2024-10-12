package dev.nebulaops.pipeline;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/pipeline-engine")
public class PipelineEngineController {
    private final Map<String, Map<String, Object>> designs = new ConcurrentHashMap<>();

    @GetMapping("/template")
    public Map<String, Object> template() {
        return Map.of(
                "version", "19.4",
                "stages", List.of("Build", "Test", "Security Scan", "Docker Build", "Helm Deploy", "Smoke Test"),
                "gitlab", gitlabYaml(),
                "argocd", Map.of("app", "nebulaops", "syncPolicy", "manual-gated", "namespace", "nebulaops")
        );
    }

    @PostMapping("/designs")
    public Map<String, Object> saveDesign(@RequestBody Map<String, Object> design) {
        String id = Objects.toString(design.getOrDefault("id", "PIPE-" + System.currentTimeMillis()));
        Map<String, Object> saved = new LinkedHashMap<>(design);
        saved.put("id", id);
        saved.put("savedAt", Instant.now().toString());
        designs.put(id, saved);
        return Map.of("status", "saved", "id", id, "design", saved);
    }

    @GetMapping("/designs")
    public Collection<Map<String, Object>> listDesigns() {
        return designs.values();
    }

    @PostMapping("/gitlab/export")
    public Map<String, Object> exportGitLab(@RequestBody(required = false) Map<String, Object> body) {
        return Map.of("filename", ".gitlab-ci.yml", "yaml", gitlabYaml(), "generatedAt", Instant.now().toString());
    }

    @PostMapping("/argocd/sync")
    public Map<String, Object> syncArgoCd(@RequestBody(required = false) Map<String, Object> body) {
        return Map.of("status", "sync-requested", "application", "nebulaops", "revision", "HEAD", "requestedAt", Instant.now().toString());
    }

    private String gitlabYaml() {
        return "stages:\n  - build\n  - test\n  - security\n  - package\n  - deploy\n  - verify\n" +
                "build:\n  stage: build\n  script: [\"npm ci\", \"mvn -q package\"]\n" +
                "test:\n  stage: test\n  script: [\"npm test\", \"mvn test\"]\n" +
                "security_scan:\n  stage: security\n  script: [\"trivy fs .\", \"gitleaks detect\"]\n" +
                "docker_build:\n  stage: package\n  script: [\"docker build -t nebulaops/frontend:$CI_COMMIT_SHA frontend\"]\n" +
                "helm_deploy:\n  stage: deploy\n  script: [\"helm upgrade --install nebulaops infrastructure/helm\"]\n" +
                "smoke_test:\n  stage: verify\n  script: [\"curl -f http://gateway-service:8080/actuator/health\"]\n";
    }
}
