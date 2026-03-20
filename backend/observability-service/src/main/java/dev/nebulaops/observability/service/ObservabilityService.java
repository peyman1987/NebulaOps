package dev.nebulaops.observability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ObservabilityService {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String defaultOrganizationId;

    public ObservabilityService(@Value("${nebulaops.default-organization-id:nebulaops}") String defaultOrganizationId) {
        this.defaultOrganizationId = defaultOrganizationId == null || defaultOrganizationId.isBlank() ? "nebulaops" : defaultOrganizationId.trim();
    }

    public Map<String, Object> stack() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(probe("prometheus", env("PROMETHEUS_URL", "http://prometheus:9090"), "/-/ready"));
        items.add(probe("loki", env("LOKI_URL", "http://loki:3100"), "/ready"));
        items.add(probe("tempo", env("TEMPO_URL", "http://tempo:3200"), "/ready"));
        items.add(probe("grafana", env("GRAFANA_URL", "http://grafana:3000"), "/api/health"));
        items.add(probe("otel-collector", env("OTEL_COLLECTOR_URL", "http://otel-collector:4318"), "/"));
        Map<String, Object> out = base("observability-stack");
        out.put("items", items);
        out.put("count", items.size());
        out.put("toolStatus", "Only runtime probes are shown; no local seeded records are generated.");
        return out;
    }

    public Map<String, Object> overview(String organizationId) {
        Map<String, Object> services = services();
        Map<String, Object> tasks = taskEvents(organizationId);
        Map<String, Object> audit = auditEvents(50);
        Map<String, Object> notifications = notificationEvents(50);
        Map<String, Object> rabbit = rabbitmq();

        Map<String, Object> out = base("observability-overview");
        out.put("organizationId", resolvedOrganization(organizationId));
        out.put("services", summarizeCollection(services));
        out.put("tasks", summarizeCollection(tasks));
        out.put("audit", summarizeCollection(audit));
        out.put("notifications", summarizeCollection(notifications));
        out.put("rabbitmq", summarizeCollection(rabbit));
        out.put("items", List.of(services, tasks, audit, notifications, rabbit));
        return out;
    }

    public Map<String, Object> services() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(probe("gateway-service", env("GATEWAY_SERVICE_URL", "http://gateway-service:8080"), "/actuator/health"));
        items.add(probe("auth-service", env("AUTH_SERVICE_URL", "http://auth-service:8081"), "/actuator/health"));
        items.add(probe("task-service", env("TASK_SERVICE_URL", "http://task-service:8082"), "/actuator/health"));
        items.add(probe("notification-service", env("NOTIFICATION_SERVICE_URL", "http://notification-service:8083"), "/actuator/health"));
        items.add(probe("file-service", env("FILE_SERVICE_URL", "http://file-service:8084"), "/actuator/health"));
        items.add(probe("ai-ops-service", env("AI_OPS_SERVICE_URL", "http://ai-ops-service:8085"), "/actuator/health"));
        items.add(probe("devsecops-service", env("DEVSECOPS_SERVICE_URL", "http://devsecops-service:8086"), "/actuator/health"));
        items.add(probe("pipeline-engine-service", env("PIPELINE_ENGINE_SERVICE_URL", "http://pipeline-engine-service:8087"), "/actuator/health"));
        items.add(probe("gitops-control-service", env("GITOPS_CONTROL_SERVICE_URL", "http://gitops-control-service:8093"), "/actuator/health"));
        items.add(probe("environment-manager-service", env("ENVIRONMENT_MANAGER_SERVICE_URL", "http://environment-manager-service:8094"), "/actuator/health"));
        items.add(probe("terraform-studio-service", env("TERRAFORM_STUDIO_SERVICE_URL", "http://terraform-studio-service:8096"), "/actuator/health"));
        items.add(probe("cost-analytics-service", env("COST_ANALYTICS_SERVICE_URL", "http://cost-analytics-service:8097"), "/actuator/health"));
        items.add(probe("release-orchestrator-service", env("RELEASE_ORCHESTRATOR_SERVICE_URL", "http://release-orchestrator-service:8098"), "/actuator/health"));
        items.add(probe("policy-governance-service", env("POLICY_GOVERNANCE_SERVICE_URL", "http://policy-governance-service:8100"), "/actuator/health"));
        items.add(probe("audit-service", env("AUDIT_SERVICE_URL", "http://audit-service:8101"), "/actuator/health"));
        Map<String, Object> out = base("service-health");
        out.put("items", items);
        out.put("count", items.size());
        out.put("up", items.stream().filter(i -> Boolean.TRUE.equals(i.get("live"))).count());
        out.put("down", items.stream().filter(i -> !Boolean.TRUE.equals(i.get("live"))).count());
        return out;
    }

    public Map<String, Object> prometheus(String query) {
        return runtimeCollection("prometheus", get(env("PROMETHEUS_URL", "http://prometheus:9090") + "/api/v1/query?query=" + enc(query == null || query.isBlank() ? "up" : query)), "data.result");
    }

    public Map<String, Object> loki(String query) {
        String q = query == null || query.isBlank() ? "{job=~\".+\"}" : query;
        return runtimeCollection("loki", get(env("LOKI_URL", "http://loki:3100") + "/loki/api/v1/query?query=" + enc(q)), "data.result");
    }

    public Map<String, Object> tempo(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return runtimeCollection("tempo", get(env("TEMPO_URL", "http://tempo:3200") + "/api/search?limit=" + safeLimit), "traces");
    }

    public Map<String, Object> grafana() {
        return get(env("GRAFANA_URL", "http://grafana:3000") + "/api/health");
    }

    public Map<String, Object> auditEvents(int limit) {
        return runtimeCollection("audit-events", get(env("AUDIT_SERVICE_URL", "http://audit-service:8101") + "/api/audit/events?limit=" + Math.max(1, Math.min(limit, 500))), "items");
    }

    public Map<String, Object> notificationEvents(int limit) {
        return runtimeCollection("notification-events", get(env("NOTIFICATION_SERVICE_URL", "http://notification-service:8083") + "/api/notifications?limit=" + Math.max(1, Math.min(limit, 500))), "items");
    }

    public Map<String, Object> taskEvents(String organizationId) {
        String base = env("RABBITMQ_HTTP_URL", "http://rabbitmq:15672").replaceAll("/+$", "");
        String vhost = enc(env("RABBITMQ_VHOST", "/"));
        String queue = enc(env("RABBITMQ_TASK_EVENTS_QUEUE", "nebula.task.events"));
        String user = env("RABBITMQ_USER", "guest");
        String pass = env("RABBITMQ_PASSWORD", "guest");
        String basic = java.util.Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
        String body = "{\"count\":100,\"ackmode\":\"ack_requeue_true\",\"encoding\":\"auto\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/queues/" + vhost + "/" + queue + "/get"))
                .timeout(Duration.ofSeconds(4))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        Map<String, Object> response = send(request, base + "/api/queues/" + vhost + "/" + queue + "/get");
        Map<String, Object> out = runtimeCollection("rabbitmq-task-events", response, "");
        out.put("queue", env("RABBITMQ_TASK_EVENTS_QUEUE", "nebula.task.events"));
        out.put("organizationId", resolvedOrganization(organizationId));
        out.put("toolStatus", Boolean.TRUE.equals(out.get("live"))
                ? "Task events read from RabbitMQ management API with ack_requeue_true; messages are not consumed."
                : "RabbitMQ task event queue unavailable or credentials missing.");
        return out;
    }

    public Map<String, Object> incidentTimeline(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<Object> items = new ArrayList<>();
        appendTimeline(items, "audit", auditEvents(safeLimit));
        appendTimeline(items, "notification", notificationEvents(safeLimit));
        appendTimeline(items, "task", taskEvents(defaultOrganizationId));
        appendTimeline(items, "loki", loki("{job=~\".+\"}"));
        appendTimeline(items, "tempo", tempo(Math.min(safeLimit, 100)));
        items.sort((a, b) -> timestampOf(b).compareTo(timestampOf(a)));
        if (items.size() > safeLimit) items = new ArrayList<>(items.subList(0, safeLimit));
        Map<String, Object> out = base("incident-timeline");
        out.put("items", items);
        out.put("count", items.size());
        out.put("toolStatus", items.isEmpty() ? "No incident timeline rows returned by live sources." : "Timeline correlated from live audit, notification, task, Loki and Tempo sources.");
        return out;
    }

    public Map<String, Object> rabbitmq() {
        String base = env("RABBITMQ_HTTP_URL", "http://rabbitmq:15672");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/api/overview")).timeout(Duration.ofSeconds(4)).GET();
        String user = env("RABBITMQ_USER", "guest");
        String pass = env("RABBITMQ_PASSWORD", "guest");
        String basic = java.util.Base64.getEncoder().encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
        builder.header("Authorization", "Basic " + basic);
        return runtimeCollection("rabbitmq", send(builder.build(), base + "/api/overview"), "queue_totals");
    }

    private Map<String, Object> probe(String name, String base, String path) {
        Map<String, Object> result = get(base + path);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", name);
        out.put("name", name);
        out.put("endpoint", base);
        out.put("probePath", path);
        out.put("live", resultLive(result));
        out.put("status", result.getOrDefault("statusCode", 0));
        out.put("durationMs", result.getOrDefault("durationMs", 0));
        out.put("body", result.get("body"));
        out.put("error", result.get("error"));
        return out;
    }

    private Map<String, Object> runtimeCollection(String source, Map<String, Object> response, String itemsPath) {
        Map<String, Object> out = base(source);
        out.put("endpoint", response.get("url"));
        out.put("statusCode", response.get("statusCode"));
        out.put("durationMs", response.get("durationMs"));
        out.put("live", resultLive(response));
        out.put("body", response.get("body"));
        out.put("error", response.get("error"));
        List<Object> items = extractItems(response.get("body"), itemsPath);
        out.put("items", items);
        out.put("count", items.size());
        out.put("toolStatus", items.isEmpty() ? "The live source returned no rows." : "Rows returned by the live source.");
        return out;
    }

    private Map<String, Object> get(String url) {
        return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).GET().build(), url);
    }

    private Map<String, Object> send(HttpRequest request, String url) {
        long started = System.nanoTime();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("url", url);
        out.put("executedAt", Instant.now().toString());
        try {
            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
            Object body;
            try {
                body = mapper.readValue(res.body(), Object.class);
            } catch (Exception e) {
                body = res.body();
            }
            out.put("live", res.statusCode() < 500);
            out.put("statusCode", res.statusCode());
            out.put("durationMs", (System.nanoTime() - started) / 1_000_000);
            out.put("body", body);
            return out;
        } catch (Exception e) {
            out.put("live", false);
            out.put("statusCode", 0);
            out.put("durationMs", (System.nanoTime() - started) / 1_000_000);
            out.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            return out;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> extractItems(Object body, String path) {
        if (body == null) return List.of();
        Object selected = body;
        if (path != null && !path.isBlank()) {
            selected = body;
            for (String part : path.split("\\.")) {
                if (selected instanceof Map<?, ?> map) selected = map.get(part);
                else return List.of();
            }
        }
        if (selected instanceof List<?> list) return new ArrayList<>((List<Object>) list);
        if (selected instanceof Map<?, ?> map) {
            if (map.get("items") instanceof List<?> list) return new ArrayList<>((List<Object>) list);
            if (map.get("result") instanceof List<?> list) return new ArrayList<>((List<Object>) list);
            List<Object> rows = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", String.valueOf(entry.getKey()));
                row.put("value", entry.getValue());
                rows.add(row);
            }
            return rows;
        }
        return List.of();
    }

    private String timestampOf(Object row) {
        if (row instanceof Map<?, ?> map) {
            Object ts = map.get("timestamp");
            return ts == null ? "" : String.valueOf(ts);
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private void appendTimeline(List<Object> target, String source, Map<String, Object> payload) {
        Object rows = payload.get("items");
        if (!(rows instanceof List<?> list)) return;
        for (Object row : list) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("source", source);
            event.put("live", payload.get("live"));
            event.put("correlationId", extractValue(row, "correlationId", "traceID", "traceId", "id"));
            event.put("timestamp", extractValue(row, "timestamp", "time", "createdAt", "executedAt", "startTime"));
            event.put("status", extractValue(row, "status", "level", "severity", "type"));
            event.put("name", extractValue(row, "name", "title", "message", "eventType", "key"));
            event.put("details", row);
            target.add(event);
        }
    }

    private Object extractValue(Object row, String... keys) {
        if (row instanceof Map<?, ?> map) {
            for (String key : keys) if (map.containsKey(key)) return map.get(key);
        }
        return "";
    }

    private Map<String, Object> summarizeCollection(Map<String, Object> source) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", source.getOrDefault("source", source.getOrDefault("name", "runtime")));
        out.put("live", source.getOrDefault("live", true));
        out.put("count", source.getOrDefault("count", 0));
        out.put("statusCode", source.getOrDefault("statusCode", 0));
        return out;
    }

    private boolean resultLive(Map<String, Object> result) {
        Object code = result.get("statusCode");
        return Boolean.TRUE.equals(result.get("live")) || (code instanceof Number n && n.intValue() > 0 && n.intValue() < 500);
    }

    private Map<String, Object> base(String source) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", true);
        out.put("source", source);
        out.put("executedAt", Instant.now().toString());
        out.put("realDataOnly", true);
        return out;
    }

    private String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String resolvedOrganization(String organizationId) {
        return organizationId == null || organizationId.isBlank() ? defaultOrganizationId : organizationId.trim();
    }

    private String env(String key, String fallback) {
        return System.getenv().getOrDefault(key, fallback);
    }
}
