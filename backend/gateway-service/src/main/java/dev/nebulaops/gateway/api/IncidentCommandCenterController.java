package dev.nebulaops.gateway.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * v23.3 — Incident Command Center.
 *
 * Aggregates only live operational signals already produced by the platform:
 * Observability, AI Ops, Notifications, Audit, Task, Kubernetes and Release Center.
 * The controller does not seed incidents. Empty sources remain empty and carry an
 * explicit toolStatus/error so the UI never falls back to mocked incident rows.
 */
@RestController
@RequestMapping("/api/incidents/command-center")
@SuppressWarnings({"unchecked", "rawtypes"})
public class IncidentCommandCenterController {

    private final RestTemplate rest;

    @Value("${proxy.ai-ops}") private String aiOpsUrl;
    @Value("${proxy.observability}") private String observabilityUrl;
    @Value("${proxy.notification}") private String notificationUrl;
    @Value("${proxy.audit}") private String auditUrl;
    @Value("${proxy.task}") private String taskUrl;
    @Value("${proxy.release}") private String releaseUrl;
    @Value("${proxy.progressive-delivery}") private String progressiveDeliveryUrl;
    @Value("${nebulaops.default-organization-id:nebulaops}") private String defaultOrganizationId;

    public IncidentCommandCenterController(RestTemplate rest) {
        this.rest = rest;
    }

    @GetMapping({"", "/"})
    public Map<String, Object> overview(@RequestParam(defaultValue = "150") int limit,
                                        @RequestParam(required = false) String correlationId,
                                        @RequestParam(required = false) String affectedService,
                                        @RequestParam(defaultValue = "default") String namespace) {
        int safeLimit = clamp(limit, 1, 500);
        Map<String, Object> incidents = incidents(safeLimit, correlationId, affectedService, namespace);
        Map<String, Object> timeline = timeline(safeLimit, correlationId);
        Map<String, Object> services = services();
        Map<String, Object> logs = logs(affectedService, correlationId);
        Map<String, Object> metrics = metrics(affectedService);
        Map<String, Object> traces = traces(Math.min(safeLimit, 100));
        Map<String, Object> notifications = notifications(safeLimit, correlationId);
        Map<String, Object> tasks = tasks(null, correlationId, affectedService);
        Map<String, Object> releases = releases(affectedService);
        Map<String, Object> kubernetes = kubernetes(namespace, affectedService);
        Map<String, Object> suggestions = suggestions(affectedService, namespace, correlationId);

        List<Map<String, Object>> rows = rows(incidents.get("items"));
        Map<String, Object> out = base("incident-command-center");
        out.put("items", rows);
        out.put("summary", Map.of(
                "incidents", rows.size(),
                "critical", rows.stream().filter(r -> severityOf(r).equals("CRITICAL")).count(),
                "high", rows.stream().filter(r -> severityOf(r).equals("HIGH")).count(),
                "open", rows.stream().filter(r -> !String.valueOf(r.getOrDefault("status", "OPEN")).equalsIgnoreCase("RESOLVED")).count(),
                "sourcesLive", liveCount(List.of(incidents, timeline, services, logs, metrics, traces, notifications, tasks, releases, kubernetes, suggestions))
        ));
        out.put("timeline", timeline);
        out.put("services", services);
        out.put("logs", logs);
        out.put("metrics", metrics);
        out.put("traces", traces);
        out.put("notifications", notifications);
        out.put("tasks", tasks);
        out.put("releases", releases);
        out.put("kubernetes", kubernetes);
        out.put("suggestions", suggestions);
        out.put("toolStatus", rows.isEmpty()
                ? "No persisted/live incidents returned by AI Ops or audit events. The command center is live-only and does not synthesize incident records."
                : "Incidents aggregated from AI Ops and audit events.");
        out.put("sourceHealth", sourceHealth(List.of(incidents, timeline, services, logs, metrics, traces, notifications, tasks, releases, kubernetes, suggestions)));
        return out;
    }

    @GetMapping("/incidents")
    public Map<String, Object> incidents(@RequestParam(defaultValue = "150") int limit,
                                         @RequestParam(required = false) String correlationId,
                                         @RequestParam(required = false) String affectedService,
                                         @RequestParam(defaultValue = "default") String namespace) {
        int safeLimit = clamp(limit, 1, 500);
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> audit = get(auditUrl + "/api/audit/events?limit=" + safeLimit + (correlationId == null ? "" : "&correlationId=" + enc(correlationId)), "audit-service");
        for (Map<String, Object> event : rows(extract(audit, "items"))) {
            if (!containsIncidentSignal(event)) continue;
            items.add(normalizeIncident(event, "audit-service"));
        }

        Map<String, Object> ai;
        if (affectedService != null && !affectedService.isBlank()) {
            ai = get(aiOpsUrl + "/api/ai-ops/incidents?affectedService=" + enc(affectedService) + "&namespace=" + enc(namespace), "ai-ops-service");
            for (Map<String, Object> row : rows(extract(ai, "items"))) items.add(normalizeIncident(row, "ai-ops-service"));
        } else {
            ai = get(aiOpsUrl + "/api/ai-ops/incidents", "ai-ops-service");
            for (Map<String, Object> row : rows(extract(ai, "items"))) items.add(normalizeIncident(row, "ai-ops-service"));
        }

        items.sort(Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.getOrDefault("createdAt", ""))).reversed());
        if (items.size() > safeLimit) items = new ArrayList<>(items.subList(0, safeLimit));
        Map<String, Object> out = base("incidents");
        out.put("items", items);
        out.put("count", items.size());
        out.put("live", Boolean.TRUE.equals(audit.get("live")) || Boolean.TRUE.equals(ai.get("live")));
        out.put("sources", Map.of("audit", slim(audit), "aiOps", slim(ai)));
        out.put("toolStatus", items.isEmpty()
                ? "No real incident records found. Provide affectedService to run an on-demand AI Ops incident analysis from live signals."
                : "Incident records loaded from runtime audit and AI Ops sources.");
        return out;
    }

    @GetMapping("/timeline")
    public Map<String, Object> timeline(@RequestParam(defaultValue = "150") int limit,
                                        @RequestParam(required = false) String correlationId) {
        Map<String, Object> raw = get(observabilityUrl + "/api/observability/incidents/timeline?limit=" + clamp(limit, 1, 500), "observability-service");
        List<Map<String, Object>> items = rows(extract(raw, "items"));
        if (correlationId != null && !correlationId.isBlank()) {
            items = items.stream().filter(row -> correlationId.equals(String.valueOf(row.getOrDefault("correlationId", "")))).toList();
        }
        Map<String, Object> out = wrap("incident-timeline", raw, items);
        out.put("correlationId", correlationId);
        return out;
    }

    @GetMapping("/services")
    public Map<String, Object> services() {
        Map<String, Object> raw = get(observabilityUrl + "/api/observability/services", "observability-service");
        return wrap("impacted-services", raw, rows(extract(raw, "items")));
    }

    @GetMapping("/logs")
    public Map<String, Object> logs(@RequestParam(required = false) String affectedService,
                                    @RequestParam(required = false) String correlationId) {
        String query = correlationId != null && !correlationId.isBlank()
                ? "{job=~\".+\"} |= \"" + safeQuery(correlationId) + "\""
                : affectedService == null || affectedService.isBlank()
                    ? "{job=~\".+\"}"
                    : "{container=~\"" + safeQuery(affectedService) + ".*\"}";
        Map<String, Object> raw = get(observabilityUrl + "/api/observability/logs/loki?query=" + enc(query), "observability-service/loki");
        Map<String, Object> out = wrap("correlated-logs", raw, rows(extract(raw, "items")));
        out.put("query", query);
        return out;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics(@RequestParam(required = false) String affectedService) {
        String query = affectedService == null || affectedService.isBlank() ? "up" : "up{job=~\".*" + safeQuery(affectedService) + ".*\"}";
        Map<String, Object> raw = get(observabilityUrl + "/api/observability/metrics/prometheus?query=" + enc(query), "observability-service/prometheus");
        Map<String, Object> out = wrap("linked-metrics", raw, rows(extract(raw, "items")));
        out.put("query", query);
        return out;
    }

    @GetMapping("/traces")
    public Map<String, Object> traces(@RequestParam(defaultValue = "50") int limit) {
        Map<String, Object> raw = get(observabilityUrl + "/api/observability/traces/tempo?limit=" + clamp(limit, 1, 100), "observability-service/tempo");
        return wrap("linked-traces", raw, rows(extract(raw, "items")));
    }

    @GetMapping("/notifications")
    public Map<String, Object> notifications(@RequestParam(defaultValue = "100") int limit,
                                             @RequestParam(required = false) String correlationId) {
        Map<String, Object> raw = get(notificationUrl + "/api/notifications?limit=" + clamp(limit, 1, 500), "notification-service");
        List<Map<String, Object>> items = rows(extract(raw, "items"));
        if (correlationId != null && !correlationId.isBlank()) {
            items = items.stream().filter(row -> JSON(row).contains(correlationId)).toList();
        }
        return wrap("incident-notifications", raw, items);
    }

    @GetMapping("/tasks")
    public Map<String, Object> tasks(@RequestParam(required = false) String organizationId,
                                     @RequestParam(required = false) String correlationId,
                                     @RequestParam(required = false) String affectedService) {
        Map<String, Object> raw = get(taskUrl + "/api/tasks?organizationId=" + enc(resolveOrganization(organizationId)), "task-service");
        List<Map<String, Object>> items = rows(raw.get("items") == null ? raw : extract(raw, "items"));
        if (correlationId != null && !correlationId.isBlank()) items = items.stream().filter(row -> JSON(row).contains(correlationId)).toList();
        if (affectedService != null && !affectedService.isBlank()) items = items.stream().filter(row -> JSON(row).toLowerCase(Locale.ROOT).contains(affectedService.toLowerCase(Locale.ROOT))).toList();
        return wrap("incident-tasks", raw, items);
    }

    @GetMapping("/releases")
    public Map<String, Object> releases(@RequestParam(required = false) String affectedService) {
        Map<String, Object> raw = get(releaseUrl + "/api/releases", "release-orchestrator-service");
        List<Map<String, Object>> items = rows(raw.get("items") == null ? raw : extract(raw, "items"));
        if (affectedService != null && !affectedService.isBlank()) items = items.stream().filter(row -> JSON(row).toLowerCase(Locale.ROOT).contains(affectedService.toLowerCase(Locale.ROOT))).toList();
        Map<String, Object> out = wrap("release-context", raw, items);
        out.put("progressiveDelivery", slim(get(progressiveDeliveryUrl + "/api/progressive-delivery/overview?namespace=all", "progressive-delivery-service")));
        return out;
    }

    @GetMapping("/kubernetes")
    public Map<String, Object> kubernetes(@RequestParam(defaultValue = "default") String namespace,
                                          @RequestParam(required = false) String affectedService) {
        String gatewayUrl = env("GATEWAY_INTERNAL_URL", "http://gateway-service:8080").replaceAll("/+$", "");
        Map<String, Object> graph = get(gatewayUrl + "/api/kubernetes/namespaces/" + enc(namespace) + "/graph", "gateway-service/kubernetes");
        Map<String, Object> events = get(gatewayUrl + "/api/kubernetes/events?namespace=" + enc(namespace), "gateway-service/kubernetes");
        Map<String, Object> pods = get(gatewayUrl + "/api/kubernetes/resources?kind=pods&namespace=" + enc(namespace), "gateway-service/kubernetes");
        Map<String, Object> out = base("kubernetes-context");
        out.put("namespace", namespace);
        out.put("affectedService", affectedService);
        out.put("graph", graph);
        out.put("events", events);
        out.put("pods", pods);
        out.put("items", rows(extract(pods, "items")));
        out.put("live", Boolean.TRUE.equals(graph.get("live")) || Boolean.TRUE.equals(events.get("live")) || Boolean.TRUE.equals(pods.get("live")));
        out.put("links", Map.of("OpenLens", "/remotes/openlens-kubernetes/", "Pod logs", "/api/kubernetes/logs?namespace=" + enc(namespace)));
        return out;
    }

    @GetMapping("/suggestions")
    public Map<String, Object> suggestions(@RequestParam(required = false) String affectedService,
                                           @RequestParam(defaultValue = "default") String namespace,
                                           @RequestParam(required = false) String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "incident-command-center");
        payload.put("affectedService", affectedService == null || affectedService.isBlank() ? "gateway-service" : affectedService);
        payload.put("namespace", namespace);
        payload.put("correlationId", correlationId);
        payload.put("requestedAt", Instant.now().toString());
        Map<String, Object> raw = post(aiOpsUrl + "/api/ai-ops/analyze", payload, "ai-ops-service");
        List<Map<String, Object>> items = new ArrayList<>();
        Object recs = nested(raw, "ai", "recommendations");
        if (recs instanceof List<?> list) {
            for (Object rec : list) items.add(Map.of("name", String.valueOf(rec), "source", "ai-ops", "type", "recommendation"));
        }
        Object localAnalysis = nested(raw, "localAnalysis", "events");
        if (localAnalysis instanceof List<?> list) for (Object rec : list) if (rec instanceof Map<?, ?> m) items.add(new LinkedHashMap<>((Map<String, Object>) m));
        Map<String, Object> out = wrap("suggested-actions", raw, items);
        out.put("toolStatus", items.isEmpty() ? String.valueOf(raw.getOrDefault("toolStatus", "AI Ops returned no suggestions from live signals.")) : "Suggestions returned by AI Ops live analysis.");
        return out;
    }

    @PostMapping("/incidents/analyze")
    public ResponseEntity<Object> analyzeIncident(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? Map.of() : body;
        return ResponseEntity.ok(post(aiOpsUrl + "/api/ai-ops/incidents/analyze", request, "ai-ops-service"));
    }

    @PostMapping("/incidents/{id}/runbook")
    public ResponseEntity<Object> runbook(@PathVariable String id) {
        return ResponseEntity.ok(post(aiOpsUrl + "/api/ai-ops/incidents/" + enc(id) + "/runbook", Map.of(), "ai-ops-service"));
    }

    @PostMapping("/incidents/{id}/tasks")
    public ResponseEntity<Object> createTask(@PathVariable String id,
                                             @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? Map.of() : body;
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("title", request.getOrDefault("title", "Incident follow-up " + id));
        task.put("description", request.getOrDefault("description", "Task generated from Incident Command Center for incident " + id));
        task.put("status", request.getOrDefault("status", "TODO"));
        task.put("priority", request.getOrDefault("priority", "HIGH"));
        task.put("projectId", request.getOrDefault("projectId", request.getOrDefault("project", "sre")));
        task.put("assigneeId", request.getOrDefault("assigneeId", "sre-oncall"));
        task.put("labels", request.getOrDefault("labels", List.of("incident", "sre", "command-center")));
        Object created = callPost(taskUrl + "/api/tasks", task, Map.of("live", false, "error", "task-service unavailable", "incidentId", id));
        return ResponseEntity.ok(created);
    }

    @GetMapping("/export")
    public ResponseEntity<Object> export(@RequestParam(defaultValue = "150") int limit,
                                         @RequestParam(required = false) String correlationId,
                                         @RequestParam(required = false) String affectedService,
                                         @RequestParam(defaultValue = "default") String namespace) {
        Map<String, Object> data = overview(limit, correlationId, affectedService, namespace);
        StringBuilder md = new StringBuilder();
        md.append("# NebulaOps Incident Command Center Report\n\n");
        md.append("Generated at: ").append(data.get("executedAt")).append("\n\n");
        md.append("## Scope\n\n");
        md.append("- Correlation ID: ").append(correlationId == null || correlationId.isBlank() ? "not filtered" : correlationId).append("\n");
        md.append("- Affected service: ").append(affectedService == null || affectedService.isBlank() ? "not filtered" : affectedService).append("\n");
        md.append("- Kubernetes namespace: ").append(namespace).append("\n\n");
        md.append("## Summary\n\n````json\n").append(JSON(data.get("summary"))).append("\n````\n\n");
        md.append("## Incidents\n\n````json\n").append(JSON(data.get("items"))).append("\n````\n\n");
        md.append("## Timeline\n\n````json\n").append(JSON(((Map<String, Object>) data.get("timeline")).get("items"))).append("\n````\n\n");
        md.append("## Linked Signals\n\n````json\n").append(JSON(Map.of(
                "services", slim((Map<String, Object>) data.get("services")),
                "logs", slim((Map<String, Object>) data.get("logs")),
                "metrics", slim((Map<String, Object>) data.get("metrics")),
                "traces", slim((Map<String, Object>) data.get("traces")),
                "tasks", slim((Map<String, Object>) data.get("tasks")),
                "releases", slim((Map<String, Object>) data.get("releases")),
                "kubernetes", slim((Map<String, Object>) data.get("kubernetes"))
        ))).append("\n````\n");
        return ResponseEntity.ok(Map.of(
                "live", true,
                "realDataOnly", true,
                "format", "markdown",
                "filename", "nebulaops-incident-command-center-report.md",
                "generatedAt", Instant.now().toString(),
                "content", md.toString(),
                "source", "incident-command-center"
        ));
    }

    private boolean containsIncidentSignal(Map<String, Object> event) {
        String raw = JSON(event).toLowerCase(Locale.ROOT);
        return raw.contains("incident") || raw.contains("sev1") || raw.contains("sev2") || raw.contains("crashloop") || raw.contains("rollback") || raw.contains("outage");
    }

    private Map<String, Object> normalizeIncident(Map<String, Object> row, String source) {
        Map<String, Object> out = new LinkedHashMap<>();
        Object payload = row.get("payload");
        Map<String, Object> nestedIncident = payload instanceof Map<?, ?> p && p.get("incident") instanceof Map<?, ?> inc ? new LinkedHashMap<>((Map<String, Object>) inc) : Map.of();
        String id = first(row, nestedIncident, "id", "incidentId", "eventId");
        out.put("id", id.isBlank() ? "incident-" + Math.abs(JSON(row).hashCode()) : id);
        out.put("name", first(row, nestedIncident, "title", "summary", "message", "type"));
        out.put("severity", upper(first(row, nestedIncident, "severity", "priority"), "UNKNOWN"));
        out.put("status", upper(first(row, nestedIncident, "status", "state"), "OPEN"));
        out.put("affectedService", first(row, nestedIncident, "affectedService", "service", "target"));
        out.put("correlationId", first(row, nestedIncident, "correlationId", "traceId", "traceID"));
        out.put("createdAt", first(row, nestedIncident, "createdAt", "timestamp", "time"));
        out.put("rootCause", first(row, nestedIncident, "rootCause", "reason", "description"));
        out.put("source", source);
        out.put("links", Map.of(
                "timeline", "/api/incidents/command-center/timeline?correlationId=" + enc(String.valueOf(out.getOrDefault("correlationId", ""))),
                "logs", "/api/incidents/command-center/logs?affectedService=" + enc(String.valueOf(out.getOrDefault("affectedService", ""))),
                "metrics", "/api/incidents/command-center/metrics?affectedService=" + enc(String.valueOf(out.getOrDefault("affectedService", ""))),
                "release", "/remotes/release-center/",
                "kubernetes", "/remotes/openlens-kubernetes/",
                "policy", "/remotes/policy-center/"
        ));
        out.put("raw", row);
        return out;
    }

    private Map<String, Object> get(String url, String source) {
        try {
            Object result = rest.getForObject(url, Object.class);
            if (result instanceof Map<?, ?> map) return normalizeResponse(new LinkedHashMap<>((Map<String, Object>) map), url, source);
            Map<String, Object> out = base(source);
            out.put("url", url);
            out.put("items", result instanceof List<?> list ? list : List.of(result));
            return out;
        } catch (ResourceAccessException e) {
            return unavailable(url, source, "UNREACHABLE", e.getMessage());
        } catch (HttpStatusCodeException e) {
            return unavailable(url, source, "HTTP_" + e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            return unavailable(url, source, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private Map<String, Object> post(String url, Object body, String source) {
        try {
            Object result = rest.postForObject(url, jsonEntity(body == null ? Map.of() : body), Object.class);
            if (result instanceof Map<?, ?> map) return normalizeResponse(new LinkedHashMap<>((Map<String, Object>) map), url, source);
            Map<String, Object> out = base(source);
            out.put("url", url);
            out.put("result", result);
            return out;
        } catch (ResourceAccessException e) {
            return unavailable(url, source, "UNREACHABLE", e.getMessage());
        } catch (HttpStatusCodeException e) {
            return unavailable(url, source, "HTTP_" + e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            return unavailable(url, source, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private Object callPost(String url, Object body, Object fallback) {
        try {
            return rest.postForObject(url, jsonEntity(body == null ? Map.of() : body), Object.class);
        } catch (Exception e) {
            return fallback;
        }
    }

    private HttpEntity<Object> jsonEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private Map<String, Object> normalizeResponse(Map<String, Object> raw, String url, String source) {
        raw.putIfAbsent("source", source);
        raw.putIfAbsent("url", url);
        raw.putIfAbsent("realDataOnly", true);
        raw.putIfAbsent("live", !raw.containsKey("error"));
        return raw;
    }

    private Map<String, Object> unavailable(String url, String source, String state, String message) {
        Map<String, Object> out = base(source);
        out.put("url", url);
        out.put("live", false);
        out.put("items", List.of());
        out.put("state", state);
        out.put("error", message == null ? state : message);
        out.put("toolStatus", source + " unavailable: " + state);
        return out;
    }

    private Map<String, Object> wrap(String source, Map<String, Object> raw, List<Map<String, Object>> items) {
        Map<String, Object> out = base(source);
        out.put("items", items);
        out.put("count", items.size());
        out.put("live", Boolean.TRUE.equals(raw.get("live")));
        out.put("sourceResponse", slim(raw));
        out.put("toolStatus", items.isEmpty() ? String.valueOf(raw.getOrDefault("toolStatus", raw.getOrDefault("error", "No rows returned by live source."))) : "Rows returned by live source.");
        return out;
    }

    private Map<String, Object> base(String source) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", source);
        out.put("executedAt", Instant.now().toString());
        out.put("realDataOnly", true);
        out.put("live", true);
        return out;
    }

    private Map<String, Object> slim(Map<String, Object> raw) {
        if (raw == null) return Map.of("live", false);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", raw.getOrDefault("source", raw.getOrDefault("name", "runtime")));
        out.put("live", raw.getOrDefault("live", false));
        out.put("count", raw.getOrDefault("count", rows(extract(raw, "items")).size()));
        out.put("state", raw.get("state"));
        out.put("statusCode", raw.get("statusCode"));
        out.put("error", raw.get("error"));
        out.put("toolStatus", raw.get("toolStatus"));
        return out;
    }

    private List<Map<String, Object>> sourceHealth(List<Map<String, Object>> sources) {
        return sources.stream().filter(Objects::nonNull).map(this::slim).toList();
    }

    private long liveCount(List<Map<String, Object>> sources) {
        return sources.stream().filter(s -> Boolean.TRUE.equals(s.get("live"))).count();
    }

    private Object extract(Map<String, Object> raw, String key) {
        if (raw == null) return List.of();
        Object v = raw.get(key);
        if (v != null) return v;
        Object body = raw.get("body");
        if (body instanceof Map<?, ?> map) { Object selected = map.get(key); return selected == null ? List.of() : selected; }
        return raw;
    }

    private List<Map<String, Object>> rows(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) out.add(new LinkedHashMap<>((Map<String, Object>) map));
                else out.add(new LinkedHashMap<>(Map.of("value", item)));
            }
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            if (map.get("items") instanceof List<?> list) return rows(list);
            if (map.get("data") instanceof List<?> list) return rows(list);
            return List.of(new LinkedHashMap<>((Map<String, Object>) map));
        }
        return List.of();
    }

    private Object nested(Map<String, Object> raw, String first, String second) {
        Object a = raw.get(first);
        if (a instanceof Map<?, ?> map) return map.get(second);
        return null;
    }

    private String first(Map<String, Object> a, Map<String, Object> b, String... keys) {
        for (String key : keys) {
            Object value = b.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
            value = a.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return "";
    }

    private String severityOf(Map<String, Object> row) { return upper(String.valueOf(row.getOrDefault("severity", "UNKNOWN")), "UNKNOWN"); }

    private String upper(String value, String fallback) {
        String v = value == null || value.isBlank() ? fallback : value;
        return v.toUpperCase(Locale.ROOT);
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private String resolveOrganization(String organizationId) {
        return organizationId == null || organizationId.isBlank()
                ? (defaultOrganizationId == null || defaultOrganizationId.isBlank() ? "nebulaops" : defaultOrganizationId.trim())
                : organizationId.trim();
    }

    private String enc(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }

    private String safeQuery(String value) { return value == null ? "" : value.replaceAll("[^A-Za-z0-9_.:-]", ""); }

    private String env(String key, String fallback) { return System.getenv().getOrDefault(key, fallback); }

    private String JSON(Object value) {
        if (value == null) return "";
        if (value instanceof Map<?, ?> map) return map.toString();
        if (value instanceof List<?> list) return list.toString();
        return String.valueOf(value);
    }
}
