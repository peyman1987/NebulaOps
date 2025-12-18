package dev.nebulaops.cost.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/cost")
public class CostController {
    private final RestTemplate rest = new RestTemplate();
    private final String auditUrl;
    private final String notificationUrl;

    public CostController(@Value("${nebulaops.audit.url:http://audit-service:8101}") String auditUrl,
                          @Value("${nebulaops.notification.url:http://notification-service:8083}") String notificationUrl) {
        this.auditUrl = auditUrl;
        this.notificationUrl = notificationUrl;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(@RequestParam(defaultValue = "monthly") String period) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("period", period);
        body.put("currency", System.getenv().getOrDefault("COST_CURRENCY", "EUR"));
        body.put("monthly", 0.0);
        body.put("delta", 0.0);
        body.put("breakdown", List.of());
        body.put("live", false);
        body.put("source", "cost-analytics-service");
        body.put("toolStatus", "No live billing adapter is configured. Configure COST_PROVIDER, cloud billing credentials, or a Kubernetes cost metrics source to populate this endpoint.");
        body.put("executedAt", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/breakdown")
    public ResponseEntity<Object> breakdown(@RequestParam(defaultValue = "monthly") String period) {
        return ResponseEntity.ok(Map.of("period", period, "items", List.of(), "live", false, "toolStatus", "No live cost breakdown source configured."));
    }

    @PostMapping("/entries")
    public ResponseEntity<Object> record(@RequestBody Map<String, Object> body) {
        publish("COST_ENTRY_RECEIVED", "INFO", Map.of("entry", body));
        return ResponseEntity.ok(Map.of("accepted", true, "persisted", false, "entry", body, "toolStatus", "Entry accepted by API; no persistent cost repository is configured.", "receivedAt", Instant.now().toString()));
    }

    @GetMapping("/services")
    public ResponseEntity<Object> servicesCost() { return ResponseEntity.ok(Map.of("items", List.of(), "live", false, "toolStatus", "No live service-level cost source configured.")); }

    @GetMapping("/forecast")
    public ResponseEntity<Object> forecast(@RequestParam(defaultValue = "next-30-days") String period, @RequestParam(defaultValue = "75.0") double threshold) {
        return ResponseEntity.ok(Map.of("period", period, "threshold", threshold, "forecast", 0.0, "thresholdStatus", "UNKNOWN", "drivers", List.of(), "live", false, "toolStatus", "No live cost history is available to compute a forecast."));
    }

    @PostMapping("/budget")
    public ResponseEntity<Object> budget(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true); result.put("persisted", false); result.put("budget", body); result.put("updatedAt", Instant.now().toString()); result.put("toolStatus", "Budget received by API; add a persistent budget repository to retain it across restarts.");
        publish("COST_BUDGET_RECEIVED", "INFO", Map.of("budget", body)); return ResponseEntity.ok(result);
    }

    @GetMapping("/anomalies") public ResponseEntity<Object> anomalies() { return ResponseEntity.ok(Map.of("items", List.of(), "live", false, "toolStatus", "No live anomaly detector source configured.")); }
    @GetMapping("/recommendations") public ResponseEntity<Object> recommendations() { return ResponseEntity.ok(Map.of("items", List.of(), "live", false, "toolStatus", "No live cost recommendation source configured.")); }

    private void publish(String type, String severity, Map<String, Object> payload) {
        String correlationId = "corr-" + UUID.randomUUID();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type); event.put("source", "cost-analytics-service"); event.put("actor", "operator"); event.put("severity", severity); event.put("correlationId", correlationId); event.put("payload", payload);
        try { rest.postForObject(auditUrl + "/api/events", event, Object.class); } catch (Exception ignored) {}
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("type", type); notification.put("source", "cost-analytics-service"); notification.put("severity", severity); notification.put("message", type + " " + correlationId); notification.put("payload", payload);
        try { rest.postForObject(notificationUrl + "/api/notifications", notification, Object.class); } catch (Exception ignored) {}
    }
}
