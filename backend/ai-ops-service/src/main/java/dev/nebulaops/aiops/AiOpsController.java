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
    private final RestTemplate rest;

    private final String aiEngineUrl;
    private final String lokiUrl;
    private final String prometheusUrl;
    private final String gatewayUrl;
    private final String auditUrl;
    private final String notificationUrl;

    public AiOpsController(AiOpsService service,
                           RestTemplate rest,
                           @Value("${aiops.engine-url:http://ai-engine:8095}") String aiEngineUrl,
                           @Value("${nebulaops.loki.url:${NEBULAOPS_LOKI_URL:http://loki:3100}}") String lokiUrl,
                           @Value("${nebulaops.prometheus.url:${NEBULAOPS_PROMETHEUS_URL:http://prometheus:9090}}") String prometheusUrl,
                           @Value("${nebulaops.gateway.url:${NEBULAOPS_GATEWAY_URL:http://gateway-service:8080}}") String gatewayUrl,
                           @Value("${nebulaops.audit.url:${NEBULAOPS_AUDIT_URL:http://audit-service:8101}}") String auditUrl,
                           @Value("${nebulaops.notification.url:${NEBULAOPS_NOTIFICATION_URL:http://notification-service:8083}}") String notificationUrl) {
        this.service = service;
        this.rest = rest;
        this.aiEngineUrl = trim(aiEngineUrl);
        this.lokiUrl = trim(lokiUrl);
        this.prometheusUrl = trim(prometheusUrl);
        this.gatewayUrl = trim(gatewayUrl);
        this.auditUrl = trim(auditUrl);
        this.notificationUrl = trim(notificationUrl);
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
        String affected = String.valueOf(input.getOrDefault("affectedService", "gateway-service"));
        String namespace = String.valueOf(input.getOrDefault("namespace", "default"));
        Map<String, Object> signals = collectSignals(namespace, affected);
        Map<String, Object> ai = analyzeThroughEngine(input, signals);
        Map<String, Object> local = service.analyze(enrichedInput(input, signals));
        return Map.of("ai", ai, "fallback", local, "signals", signals, "live", true, "generatedAt", Instant.now().toString());
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
        Map<String, Object> ai = analyzeThroughEngine(input, signals);
        List<String> evidence = List.of(
            "Loki: " + summarize(signals.get("loki")),
            "Prometheus: " + summarize(signals.get("prometheus")),
            "Kubernetes events: " + summarize(signals.get("kubernetesEvents"))
        );

        String rootCause = str(ai.getOrDefault("rootCause", inferRootCause(signals)));
        String severity = str(ai.getOrDefault("severity", inferSeverity(signals)));
        List<String> recommendations = listOf(ai.get("recommendations"));
        if (recommendations.isEmpty() && liveSignalAvailable(signals)) {
            recommendations = recommendations(rootCause);
        }

        Map<String, Object> incident = new LinkedHashMap<>();
        incident.put("id", "inc-" + UUID.randomUUID());
        incident.put("severity", input.getOrDefault("severity", severity));
        incident.put("source", ai.getOrDefault("provider", "live-signals"));
        incident.put("affectedService", affected);
        incident.put("symptoms", input.getOrDefault("symptoms", evidence));
        incident.put("rootCause", rootCause);
        incident.put("evidence", ai.getOrDefault("evidence", evidence));
        incident.put("signals", signals);
        incident.put("ai", ai);
        incident.put("recommendations", recommendations);
        incident.put("status", "OPEN");
        incident.put("correlationId", correlationId);
        incident.put("createdAt", Instant.now().toString());
        incident.put("live", true);

        publish("INCIDENT_ANALYZED", "CRITICAL".equals(severity) || "HIGH".equals(severity) ? "HIGH" : "WARN", correlationId, Map.of("incident", incident));
        return incident;
    }

    @GetMapping("/incidents")
    public Map<String, Object> incidents(@RequestParam(required = false) String affectedService,
                                         @RequestParam(defaultValue = "default") String namespace) {
        if (affectedService == null || affectedService.isBlank()) {
            return Map.of("items", List.of(), "live", false,
                "toolStatus", "No affectedService provided. Use /api/ai-ops/incidents/analyze to create an RCA from live Loki/Prometheus/Kubernetes signals.");
        }
        return Map.of("items", List.of(incidentsAnalyze(Map.of("affectedService", affectedService, "namespace", namespace, "source", "on-demand-live-check"))), "live", true);
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

    private Map<String, Object> analyzeThroughEngine(Map<String, Object> input, Map<String, Object> signals) {
        Map<String, Object> payload = enrichedInput(input, signals);
        payload.putIfAbsent("prompt", "Analyze NebulaOps runtime signals and return RCA JSON.");
        Map<String, Object> unavailable = new LinkedHashMap<>();
        unavailable.put("provider", "ai-engine");
        unavailable.put("llmAvailable", false);
        unavailable.put("rootCause", inferRootCause(signals));
        unavailable.put("severity", inferSeverity(signals));
        unavailable.put("recommendations", liveSignalAvailable(signals) ? recommendations(inferRootCause(signals)) : List.of());
        unavailable.put("toolStatus", "AI Engine unavailable at " + aiEngineUrl);
        return post(aiEngineUrl + "/analyze", payload, unavailable);
    }

    private Map<String, Object> enrichedInput(Map<String, Object> input, Map<String, Object> signals) {
        Map<String, Object> payload = new LinkedHashMap<>(input);
        payload.put("signals", signals);
        return payload;
    }

    private Map<String, Object> collectSignals(String namespace, String affected) {
        return Map.of(
            "loki", lokiQuery(affected),
            "prometheus", prometheusQuery("up"),
            "kubernetesEvents", get(gatewayUrl + "/api/kubernetes/events?namespace=" + encode(namespace), Map.of("live", false, "toolStatus", "Kubernetes events unavailable")),
            "kubernetesGraph", get(gatewayUrl + "/api/kubernetes/namespaces/" + encode(namespace) + "/graph", Map.of("live", false, "toolStatus", "Kubernetes graph unavailable"))
        );
    }

    private Map<String, Object> lokiQuery(String serviceName) {
        String query = "{container=~\"" + serviceName + ".*\"}";
        return get(lokiUrl + "/loki/api/v1/query?query=" + encode(query), Map.of("live", false, "toolStatus", "Loki unavailable", "query", query));
    }

    private Map<String, Object> prometheusQuery(String query) {
        return get(prometheusUrl + "/api/v1/query?query=" + encode(query), Map.of("live", false, "toolStatus", "Prometheus unavailable", "query", query));
    }

    private boolean liveSignalAvailable(Map<String, Object> signals) {
        String s = String.valueOf(signals).toLowerCase(Locale.ROOT);
        return s.contains("\"live\":true") || s.contains("live=true") || s.contains("status=200") || s.contains("statuscode=200");
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
        if (s.contains("crashloopbackoff") || s.contains("imagepullbackoff") || s.contains("oomkilled")) return "CRITICAL";
        if (s.contains("connection refused") || s.contains("error")) return "HIGH";
        if (s.contains("warn")) return "WARN";
        return "INFO";
    }

    private List<String> recommendations(String rootCause) {
        String lower = rootCause.toLowerCase(Locale.ROOT);
        if (lower.contains("image")) return List.of("Verify image tag and registry credentials", "Check DevSecOps scan output", "Rollback to previous immutable image tag if the new image cannot be pulled");
        if (lower.contains("memory")) return List.of("Review memory limits and container stats", "Scale workload or increase limit", "Inspect recent release changes");
        if (lower.contains("dependency")) return List.of("Check downstream service health", "Verify Docker/Compose DNS", "Open Release Center health-impact view");
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
        post(notificationUrl + "/api/notifications", Map.of("type", type, "source", "ai-ops-service", "severity", severity, "message", type + " " + correlationId, "payload", payload), Map.of());
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
        try { return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8); }
        catch (Exception e) { return value; }
    }

    private String summarize(Object o) {
        String s = String.valueOf(o);
        return s.length() > 180 ? s.substring(0, 180) + "..." : s;
    }

    private String trim(String value) { return value == null ? "" : value.replaceAll("/+$", ""); }
    private String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private List<String> listOf(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object item : list) out.add(String.valueOf(item));
        return out;
    }
}
