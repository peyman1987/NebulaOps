package dev.nebulaops.gateway.api;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/docs")
public class DocsController {

    @GetMapping
    public Map<String, Object> index() {
        return Map.of(
            "version", "22.4.0",
            "services", List.of(
                service("gateway-service", "/v3/api-docs", "API edge and runtime proxy"),
                service("release-orchestrator-service", "/api/docs/release-orchestrator-service", "Release Center API"),
                service("policy-governance-service", "/api/docs/policy-governance-service", "Policy Center API"),
                service("audit-service", "/api/docs/audit-service", "Audit and platform events API"),
                service("ai-ops-service", "/api/docs/ai-ops-service", "Incident analysis and RCA API"),
                service("devsecops-service", "/api/docs/devsecops-service", "Scan, SBOM and security report API"),
                service("cost-analytics-service", "/api/docs/cost-analytics-service", "FinOps forecast and budget API")
            )
        );
    }

    @GetMapping("/{service}")
    public Map<String, Object> service(@PathVariable String service) {
        return Map.of(
            "service", service,
            "version", "22.4.0",
            "openapi", "docs/openapi/" + service + ".openapi.yml",
            "gatewayBase", "/api",
            "status", "documented"
        );
    }

    private Map<String, Object> service(String name, String docs, String description) {
        return Map.of("name", name, "docs", docs, "description", description);
    }
}
