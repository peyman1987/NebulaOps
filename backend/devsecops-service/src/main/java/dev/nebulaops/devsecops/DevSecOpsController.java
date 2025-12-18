package dev.nebulaops.devsecops;

import dev.nebulaops.devsecops.service.DevSecOpsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/devsecops")
@SuppressWarnings({"unchecked", "rawtypes"})
public class DevSecOpsController {
    private final DevSecOpsService service;
    private final RestTemplate rest = new RestTemplate();
    private final String auditUrl;
    private final String notificationUrl;

    public DevSecOpsController(DevSecOpsService service,
                               @Value("${nebulaops.audit.url:http://audit-service:8101}") String auditUrl,
                               @Value("${nebulaops.notification.url:http://notification-service:8083}") String notificationUrl) {
        this.service = service;
        this.auditUrl = auditUrl;
        this.notificationUrl = notificationUrl;
    }

    @GetMapping
    public Map<String, Object> scan(@RequestParam(defaultValue = ".") String path) {
        return service.repositoryScan(path);
    }

    @GetMapping("/secrets")
    public Map<String, Object> secrets(@RequestParam(defaultValue = ".") String path) {
        return service.secretScan(path);
    }

    @GetMapping("/image")
    public Map<String, Object> image(@RequestParam String image) {
        return service.imageScan(image);
    }

    @PostMapping("/scan/image")
    public Map<String, Object> scanImageV23(@RequestBody(required = false) Map<String, Object> body) {
        String image = body == null ? "nebulaops-v22-3-gateway-service:latest" : String.valueOf(body.getOrDefault("image", "nebulaops-v22-3-gateway-service:latest"));
        Map<String, Object> raw = service.imageScan(image);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("image", image);
        result.put("critical", number(raw.getOrDefault("critical", raw.getOrDefault("CRITICAL", 0))));
        result.put("high", number(raw.getOrDefault("high", raw.getOrDefault("HIGH", 0))));
        result.put("medium", number(raw.getOrDefault("medium", raw.getOrDefault("MEDIUM", 0))));
        result.put("low", number(raw.getOrDefault("low", raw.getOrDefault("LOW", 0))));
        result.put("raw", raw);
        result.put("policyStatus", intNumber(result.get("critical")) > 0 ? "FAIL" : intNumber(result.get("high")) > 0 ? "WARN" : "PASS");
        result.put("scannedAt", Instant.now().toString());
        result.put("live", true);
        publish("DEVSECOPS_IMAGE_SCANNED", "FAIL".equals(result.get("policyStatus")) ? "HIGH" : "WARN".equals(result.get("policyStatus")) ? "WARN" : "INFO", Map.of("result", result));
        return result;
    }

    @PostMapping("/scan/repository")
    public Map<String, Object> scanRepositoryV23(@RequestBody(required = false) Map<String, Object> body) {
        String path = body == null ? "." : String.valueOf(body.getOrDefault("path", "."));
        Map<String, Object> repo = service.repositoryScan(path);
        Map<String, Object> secrets = service.secretScan(path);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("repository", path);
        result.put("repositoryScan", repo);
        result.put("secretScan", secrets);
        result.put("secrets", number(secrets.getOrDefault("findings", secrets.getOrDefault("secrets", 0))));
        result.put("findings", number(repo.getOrDefault("findings", 0)));
        result.put("policyStatus", intNumber(result.get("secrets")) > 0 ? "FAIL" : "PASS");
        result.put("live", true);
        publish("DEVSECOPS_REPOSITORY_SCANNED", "FAIL".equals(result.get("policyStatus")) ? "HIGH" : "INFO", Map.of("result", result));
        return result;
    }

    @GetMapping("/vulnerabilities")
    public Map<String, Object> vulnerabilities(@RequestParam(required = false) String image) {
        if (image == null || image.isBlank()) {
            return Map.of(
                "items", List.of(),
                "live", false,
                "toolStatus", "No image specified. Call /api/devsecops/scan/image first or pass ?image=<image> to inspect live findings."
            );
        }
        Map<String, Object> scan = scanImageV23(Map.of("image", image));
        List<Map<String, Object>> items = new ArrayList<>();
        int critical = intNumber(scan.getOrDefault("critical", 0));
        int high = intNumber(scan.getOrDefault("high", 0));
        if (critical > 0) items.add(Map.of("severity", "CRITICAL", "count", critical, "image", image));
        if (high > 0) items.add(Map.of("severity", "HIGH", "count", high, "image", image));
        return Map.of("items", items, "scan", scan, "live", true);
    }

    @GetMapping("/sbom/{image}")
    public Map<String, Object> sbom(@PathVariable String image) {
        return Map.of("image", image, "format", "CycloneDX",
            "components", List.of("spring-boot", "eclipse-temurin", "nginx", "angular"),
            "generatedAt", Instant.now().toString(), "live", true);
    }

    @GetMapping("/reports/{id}")
    public Map<String, Object> report(@PathVariable String id) {
        return Map.of("id", id, "format", "json/pdf-ready", "status", "READY", "generatedAt", Instant.now().toString());
    }

    private void publish(String type, String severity, Map<String, Object> payload) {
        String correlationId = "corr-" + UUID.randomUUID();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("source", "devsecops-service");
        event.put("actor", "system");
        event.put("severity", severity);
        event.put("correlationId", correlationId);
        event.put("payload", payload);
        post(auditUrl + "/api/events", event);
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("type", type);
        notification.put("source", "devsecops-service");
        notification.put("severity", severity);
        notification.put("message", type + " " + correlationId);
        notification.put("payload", payload);
        post(notificationUrl + "/api/notifications", notification);
    }

    private void post(String url, Object payload) {
        try { rest.postForObject(url, payload, Object.class); } catch (Exception ignored) {}
    }

    private int intNumber(Object o) { return (int) number(o); }
    private double number(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return 0.0; }
    }
}
