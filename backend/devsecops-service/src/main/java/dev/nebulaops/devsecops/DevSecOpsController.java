package dev.nebulaops.devsecops;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/devsecops")
public class DevSecOpsController {
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return Map.of(
                "version", "19.3",
                "riskScore", 87,
                "updatedAt", Instant.now().toString(),
                "scans", List.of(
                        scan("SCAN-TRIVY-API", "Trivy", "gateway-service:19.3.0", "RUNNING", 1, 4, 9),
                        scan("SCAN-DOCKER-FE", "Docker", "frontend:19.3.0", "PASSED", 0, 1, 4),
                        scan("SCAN-SAST-BE", "SAST", "backend/**/*.java", "FAILED", 1, 3, 7),
                        scan("SCAN-SECRETS", "Secrets", "repo tree", "PASSED", 0, 0, 2)
                ),
                "cves", List.of(
                        cve("CVE-2025-7421", "netty-codec-http2", "CRITICAL", "4.1.118+"),
                        cve("CVE-2025-2198", "openssl", "HIGH", "3.3.4-r1"),
                        cve("CVE-2024-9982", "lodash", "HIGH", "4.17.22+")
                ),
                "note", "Demo-safe scanner model. Wire these endpoints to Trivy, dependency-check, gitleaks and SAST tools for production."
        );
    }

    @PostMapping("/scan")
    public Map<String, Object> scanNow(@RequestBody(required = false) Map<String, Object> body) {
        String target = Objects.toString(body == null ? "repo tree" : body.getOrDefault("target", "repo tree"));
        return Map.of("status", "started", "target", target, "scanId", "DEVSECOPS-19-3-" + System.currentTimeMillis(), "startedAt", Instant.now().toString());
    }

    private Map<String, Object> scan(String id, String tool, String target, String status, int critical, int high, int medium) {
        return Map.of("id", id, "tool", tool, "target", target, "status", status, "critical", critical, "high", high, "medium", medium);
    }

    private Map<String, Object> cve(String id, String pkg, String severity, String fix) {
        return Map.of("cve", id, "packageName", pkg, "severity", severity, "fixVersion", fix);
    }
}
