package dev.nebulaops.aiops;

import dev.nebulaops.aiops.service.AiOpsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping({"/api/aiops", "/api/ai-ops"})
@SuppressWarnings({"unchecked", "rawtypes"})
public class AiOpsController {
    private final AiOpsService service;
    private final RestTemplate rest = new RestTemplate();

    private final String lokiUrl;
    private final String prometheusUrl;
    private final String gatewayUrl;
    private final String auditUrl;
    private final String notificationUrl;

    public AiOpsController(AiOpsService service,
                           @Value("${nebulaops.loki.url:http://loki:3100}") String lokiUrl,
                           @Value("${nebulaops.prometheus.url:http://prometheus:9090}") String prometheusUrl,
                           @Value("${nebulaops.gateway.url:http://gateway-service:8080}") String gatewayUrl,
                           @Value("${nebulaops.audit.url:http://audit-service:8101}") String auditUrl,
                           @Value("${nebulaops.notification.url:http://notification-service:8083}") String notificationUrl) {
        this.service = service;
        this.lokiUrl = lokiUrl;
        this.prometheusUrl = prometheusUrl;
        this.gatewayUrl = gatewayUrl;
        this.auditUrl = auditUrl;
        this.notificationUrl = notificationUrl;
    }

    @GetMapping("/diagnose")
    public Map<String, Object> diagnose(@RequestParam(required = false) String namespace) {
        Map<String, Object> base = service.diagnose(namespace);
        Map<String, Object> live = collectSignals(namespace == null ? "default" : namespace, "gateway-service");
        return Map.of("base", base, "liveSignals", live, "timestamp", Instant.now().toString());
    }

    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> input = body == null ? Map.of() : body;
        Map<String, Object> base = service.analyze(input);
        Map<String, Object> incident = incidentsAnalyze(input);
        return Map.of("base", base, "incident", incident, "live", true);
    }

    @PostMapping("/autofix")
    public Map<String, Object> autofix(@RequestBody(required = false) Map<String, Object> body) {
        return service.autofix(body == null ? Map.of() : body);
    }

    @GetMapping("/logs")
    public Map<String, Object> logs(@RequestParam(required = false) String selector, @RequestParam(defaultValue = "default") String namespace) {
        Map<String, Object> loki = lokiQuery(selector == null ? "gateway-service" : selector);
        return Map.of("namespace", namespace, "selector", selector, "loki", loki, "kubernetesLogs", service.logs(selector, namespace));
    }

    @PostMapping("/actions/{action}")
    public Map<String, Object> action(@PathVariable String action, @RequestParam String deployment, @RequestParam(defaultValue = "default") String namespace) {
        return service.action(namespace, deployment, action);
    }

    @PostMapping("/incidents/analyze")
    public Map<String, Object> incidentsAnalyze(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> input = body == null ? Map.of() : body;
        String affected = String.valueOf(input.getOrDefault("affectedService", "gateway-service"));
        String namespace = String.valueOf(input.getOrDefault("namespace", "default"));
        String correlationId = "corr-" + UUID.randomUUID();

        Map<String, Object> signals = collectSignals(namespace, affected);
        List<String> evidence = new ArrayList<>();
        evidence.add("Loki: " + summarize(signals.get("loki")));
        evidence.add("Prometheus: " + summarize(signals.get("prometheus")));
        evidence.add("Kubernetes events: " + summarize(signals.get("kubernetesEvents")));

        String rootCause = inferRootCause(signals);
        String severity = inferSeverity(signals);
        List<String> recommendations = liveSignalAvailable(signals) ? recommendations(rootCause) : List.of();

        Map<String, Object> incident = new LinkedHashMap<>();
        incident.put("id", "inc-" + UUID.randomUUID());
        incident.put("severity", input.getOrDefault("severity", severity));
        incident.put("source", input.getOrDefault("source", "live-signals"));
        incident.put("affectedService", affected);
        incident.put("symptoms", input.getOrDefault("symptoms", evidence));
        incident.put("rootCause", rootCause);
        incident.put("evidence", evidence);
        incident.put("signals", signals);
        incident.put("recommendations", recommendations);
        incident.put("status", "OPEN");
        incident.put("correlationId", correlationId);
        incident.put("createdAt", Instant.now().toString());
        incident.put("live", true);

        publish("INCIDENT_ANALYZED", "HIGH".equals(severity) ? "HIGH" : "WARN", correlationId, Map.of("incident", incident));
        return incident;
    }

    @GetMapping("/incidents")
    public Map<String, Object> incidents(@RequestParam(required = false) String affectedService,
                                         @RequestParam(defaultValue = "default") String namespace) {
        if (affectedService == null || affectedService.isBlank()) {
            return Map.of(
                "items", List.of(),
                "live", false,
                "toolStatus", "No affectedService provided. Use /api/ai-ops/incidents/analyze to create an RCA from live Loki/Prometheus/Kubernetes signals."
            );
        }
        return Map.of("items", List.of(incidentsAnalyze(Map.of(
            "affectedService", affectedService,
            "namespace", namespace,
            "source", "on-demand-live-check"
        ))), "live", true);
    }

    @GetMapping("/incidents/{id}")
    public Map<String, Object> incident(@PathVariable String id) {
        return Map.of("id", id, "live", false, "items", List.of(), "toolStatus", "No persisted incident repository is configured. Create incidents through /api/ai-ops/incidents/analyze using live signals.");
    }

    @PostMapping("/incidents/{id}/runbook")
    public Map<String, Object> incidentRunbook(@PathVariable String id) {
        return Map.of("id", id, "live", false, "runbook", List.of(), "toolStatus", "No live runbook engine or persisted incident repository is configured.");
    }

    @PostMapping("/incidents/{id}/create-task")
    public Map<String, Object> incidentCreateTask(@PathVariable String id) {
        String taskId = "task-" + UUID.randomUUID();
        publish("INCIDENT_TASK_CREATED", "INFO", "corr-" + UUID.randomUUID(), Map.of("incidentId", id, "taskId", taskId));
        return Map.of("incidentId", id, "taskId", taskId, "status", "CREATED");
    }

    private Map<String, Object> collectSignals(String namespace, String affected) {
        return Map.of(
            "loki", lokiQuery(affected),
            "prometheus", prometheusQuery("up"),
            "kubernetesEvents", get(gatewayUrl + "/api/kubernetes/events?namespace=" + namespace, Map.of("live", false)),
            "kubernetesGraph", get(gatewayUrl + "/api/kubernetes/namespaces/" + namespace + "/graph", Map.of("live", false))
        );
    }

    private Map<String, Object> lokiQuery(String serviceName) {
        String query = "{container=~\"" + serviceName + ".*\"}";
        String url = lokiUrl + "/loki/api/v1/query?query=" + encode(query);
        return get(url, Map.of("live", false, "toolStatus", "Loki unavailable", "query", query));
    }

    private Map<String, Object> prometheusQuery(String query) {
        String url = prometheusUrl + "/api/v1/query?query=" + encode(query);
        return get(url, Map.of("live", false, "toolStatus", "Prometheus unavailable", "query", query));
    }

    private boolean liveSignalAvailable(Map<String, Object> signals) {
        return String.valueOf(signals).toLowerCase(Locale.ROOT).contains("\"live\":true")
            || String.valueOf(signals).toLowerCase(Locale.ROOT).contains("live=true");
    }

    private String inferRootCause(Map<String, Object> signals) {
        String s = String.valueOf(signals).toLowerCase(Locale.ROOT);
        if (s.contains("crashloopbackoff")) return "Kubernetes workload is restarting repeatedly";
        if (s.contains("imagepullbackoff")) return "Kubernetes cannot pull the requested container image";
        if (s.contains("oomkilled")) return "Container memory limit was exceeded";
        if (s.contains("connection refused")) return "A downstream runtime dependency is unavailable";
        if (s.contains("5xx") || s.contains("error")) return "Application errors detected in logs or metrics";
        return "No single critical root cause detected; inspect correlated logs, metrics and Kubernetes events";
    }

    private String inferSeverity(Map<String, Object> signals) {
        String s = String.valueOf(signals).toLowerCase(Locale.ROOT);
        if (s.contains("crashloopbackoff") || s.contains("imagepullbackoff") || s.contains("oomkilled") || s.contains("connection refused")) return "HIGH";
        if (s.contains("warn") || s.contains("error")) return "WARN";
        return "INFO";
    }

    private List<String> recommendations(String rootCause) {
        if (rootCause.toLowerCase(Locale.ROOT).contains("image")) {
            return List.of("Verify image tag and registry credentials", "Check DevSecOps scan output", "Rollback to previous immutable image tag");
        }
        if (rootCause.toLowerCase(Locale.ROOT).contains("memory")) {
            return List.of("Review memory limits and container stats", "Scale workload or increase limit", "Inspect recent release changes");
        }
        if (rootCause.toLowerCase(Locale.ROOT).contains("dependency")) {
            return List.of("Check downstream service health", "Verify Docker/Compose DNS", "Open Release Center health-impact view");
        }
        return List.of("Review Loki and Prometheus evidence", "Run Policy Center evaluation", "Use Release Center rollback only if SLO is affected");
    }

    private void publish(String type, String severity, String correlationId, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("source", "ai-ops-service");
        event.put("actor", "system");
        event.put("severity", severity);
        event.put("correlationId", correlationId);
        event.put("payload", payload);
        post(auditUrl + "/api/events", event, Map.of());
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("type", type);
        notification.put("source", "ai-ops-service");
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

    private String encode(String value) {
        try { return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8); }
        catch (Exception e) { return value; }
    }

    private String summarize(Object o) {
        String s = String.valueOf(o);
        return s.length() > 180 ? s.substring(0, 180) + "..." : s;
    }
}
