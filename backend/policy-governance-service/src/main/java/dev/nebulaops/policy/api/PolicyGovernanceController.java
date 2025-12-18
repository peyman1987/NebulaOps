package dev.nebulaops.policy.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/policies")
@SuppressWarnings({"unchecked", "rawtypes"})
public class PolicyGovernanceController {
    private final RestTemplate rest = new RestTemplate();
    private final List<Map<String,Object>> policies = new ArrayList<>();

    private final String gatewayUrl;
    private final String devsecopsUrl;
    private final String costUrl;
    private final String auditUrl;
    private final String notificationUrl;

    public PolicyGovernanceController(
        @Value("${nebulaops.gateway.url:http://gateway-service:8080}") String gatewayUrl,
        @Value("${nebulaops.devsecops.url:http://devsecops-service:8086}") String devsecopsUrl,
        @Value("${nebulaops.cost.url:http://cost-analytics-service:8097}") String costUrl,
        @Value("${nebulaops.audit.url:http://audit-service:8101}") String auditUrl,
        @Value("${nebulaops.notification.url:http://notification-service:8083}") String notificationUrl
    ) {
        this.gatewayUrl = gatewayUrl;
        this.devsecopsUrl = devsecopsUrl;
        this.costUrl = costUrl;
        this.auditUrl = auditUrl;
        this.notificationUrl = notificationUrl;
    }

    private Map<String,Object> policy(String id, String name, String domain, String severity, String rule) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", id);
        p.put("name", name);
        p.put("domain", domain);
        p.put("severity", severity);
        p.put("enabled", true);
        p.put("rule", rule);
        p.put("updatedAt", Instant.now().toString());
        return p;
    }

    @GetMapping
    public ResponseEntity<Object> list() {
        return ResponseEntity.ok(Map.of("count", policies.size(), "items", policies, "live", true));
    }

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody Map<String,Object> body) {
        Map<String,Object> p = new LinkedHashMap<>();
        p.put("id", "pol-" + UUID.randomUUID());
        p.put("name", body.getOrDefault("name","custom policy"));
        p.put("domain", body.getOrDefault("domain","platform"));
        p.put("severity", body.getOrDefault("severity","WARN"));
        p.put("enabled", body.getOrDefault("enabled", true));
        p.put("rule", body.getOrDefault("rule","manual review"));
        p.put("updatedAt", Instant.now().toString());
        policies.add(0, p);
        publish("POLICY_CREATED", "INFO", "corr-" + UUID.randomUUID(), Map.of("policy", p));
        return ResponseEntity.ok(p);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable String id, @RequestBody Map<String,Object> body) {
        Map<String, Object> updated = new LinkedHashMap<>(body);
        updated.put("id", id);
        updated.put("updated", true);
        updated.put("updatedAt", Instant.now().toString());
        publish("POLICY_UPDATED", "INFO", "corr-" + UUID.randomUUID(), Map.of("policy", updated));
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable String id) {
        policies.removeIf(p -> id.equals(p.get("id")));
        publish("POLICY_DELETED", "WARN", "corr-" + UUID.randomUUID(), Map.of("policyId", id));
        return ResponseEntity.ok(Map.of("id", id, "deleted", true));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<Object> evaluate(@RequestBody(required=false) Map<String,Object> body) {
        Map<String, Object> input = body == null ? Map.of() : body;
        Map<String, Object> release = asMap(input.get("release"));
        String target = String.valueOf(input.getOrDefault("target", release.getOrDefault("application", "nebulaops-platform")));
        String image = String.valueOf(release.getOrDefault("image", input.getOrDefault("image", "nebulaops-v22-3-" + target + ":latest")));
        double budgetThreshold = number(input.getOrDefault("budgetThreshold", 75.0));

        Map<String, Object> devsecops = post(devsecopsUrl + "/api/devsecops/scan/image", Map.of("image", image),
            Map.of("live", false, "critical", 0, "high", 0, "policyStatus", "UNKNOWN"));
        Map<String, Object> cost = get(costUrl + "/api/cost/forecast",
            Map.of("live", false, "forecast", 0, "currency", "EUR"));
        Map<String, Object> kubernetes = get(gatewayUrl + "/api/kubernetes/health",
            Map.of("live", false, "healthy", false, "toolStatus", "Kubernetes unavailable"));

        int critical = intNumber(devsecops.getOrDefault("critical", 0));
        int high = intNumber(devsecops.getOrDefault("high", 0));
        double forecast = number(cost.getOrDefault("forecast", cost.getOrDefault("monthly", 0)));
        boolean healthOk = !String.valueOf(kubernetes).toLowerCase(Locale.ROOT).contains("down")
                        && !String.valueOf(kubernetes).toLowerCase(Locale.ROOT).contains("crashloopbackoff")
                        && !String.valueOf(kubernetes).toLowerCase(Locale.ROOT).contains("imagepullbackoff");

        List<Map<String, Object>> results = new ArrayList<>();
        results.add(result("pol-no-latest", image.endsWith(":latest") ? "WARN" : "PASS", image.endsWith(":latest") ? "Image tag uses latest" : "Image tag is versioned"));
        results.add(result("pol-critical-vuln", critical > 0 ? "FAIL" : (high > 0 ? "WARN" : "PASS"), "critical=" + critical + ", high=" + high));
        results.add(result("pol-cost-threshold", forecast > budgetThreshold ? "FAIL" : "PASS", "forecast=" + forecast + ", threshold=" + budgetThreshold));
        results.add(result("pol-health-gate", healthOk ? "PASS" : "FAIL", "kubernetes health evaluated"));

        boolean fail = results.stream().anyMatch(r -> "FAIL".equals(r.get("status")));
        boolean warn = results.stream().anyMatch(r -> "WARN".equals(r.get("status")));
        String status = fail ? "FAIL" : warn ? "WARN" : "PASS";
        boolean allowPromotion = !fail;

        Map<String, Object> evaluation = new LinkedHashMap<>();
        evaluation.put("id", "eval-" + UUID.randomUUID());
        evaluation.put("target", target);
        evaluation.put("image", image);
        evaluation.put("status", status);
        evaluation.put("allowPromotion", allowPromotion);
        evaluation.put("results", results);
        evaluation.put("signals", Map.of("devsecops", devsecops, "cost", cost, "kubernetes", kubernetes));
        evaluation.put("evaluatedAt", Instant.now().toString());
        String cid = "corr-" + UUID.randomUUID();
        evaluation.put("correlationId", cid);

        publish("POLICY_EVALUATED", allowPromotion ? "INFO" : "WARN", cid, Map.of("evaluation", evaluation));
        return ResponseEntity.ok(evaluation);
    }

    @GetMapping("/evaluations")
    public ResponseEntity<Object> evaluations() {
        return ResponseEntity.ok(Map.of(
            "items", List.of(),
            "live", false,
            "toolStatus", "Policy evaluations are generated on demand through POST /api/policies/evaluate. No persisted evaluation store is configured."
        ));
    }

    private Map<String, Object> result(String id, String status, String message) {
        return Map.of("policyId", id, "status", status, "message", message);
    }

    private void publish(String type, String severity, String correlationId, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("source", "policy-governance-service");
        event.put("actor", "operator");
        event.put("severity", severity);
        event.put("correlationId", correlationId);
        event.put("payload", payload);
        post(auditUrl + "/api/events", event, Map.of());
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("type", type);
        notification.put("source", "policy-governance-service");
        notification.put("severity", severity);
        notification.put("message", type + " " + correlationId);
        notification.put("payload", payload);
        post(notificationUrl + "/api/notifications", notification, Map.of());
    }

    private Map<String, Object> get(String url, Map<String, Object> unavailablePayload) {
        try {
            Object body = rest.getForObject(url, Object.class);
            if (body instanceof Map) return (Map<String, Object>) body;
            return Map.of("live", true, "items", body == null ? List.of() : body);
        } catch (Exception e) {
            Map<String, Object> m = new LinkedHashMap<>(unavailablePayload);
            m.put("error", e.getMessage());
            return m;
        }
    }

    private Map<String, Object> post(String url, Object payload, Map<String, Object> unavailablePayload) {
        try {
            Object body = rest.postForObject(url, payload, Object.class);
            if (body instanceof Map) return (Map<String, Object>) body;
            return Map.of("live", true, "result", body == null ? Map.of() : body);
        } catch (Exception e) {
            Map<String, Object> m = new LinkedHashMap<>(unavailablePayload);
            m.put("error", e.getMessage());
            return m;
        }
    }

    private Map<String, Object> asMap(Object o) { return o instanceof Map ? (Map<String, Object>) o : Collections.emptyMap(); }
    private int intNumber(Object o) { return (int) number(o); }
    private double number(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0.0; }
    }
}
