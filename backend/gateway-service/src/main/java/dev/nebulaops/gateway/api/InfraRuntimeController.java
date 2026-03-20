package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.service.DockerRuntimeService;
import dev.nebulaops.gateway.service.ObservabilityPlatformService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/platform/infra")
public class InfraRuntimeController {
    private final RestTemplate rest = new RestTemplate();
    private final DockerRuntimeService docker;
    private final ObservabilityPlatformService observability;

    public InfraRuntimeController(DockerRuntimeService docker, ObservabilityPlatformService observability) {
        this.docker = docker;
        this.observability = observability;
    }

    @GetMapping("/services")
    public Map<String, Object> services() {
        List<Map<String, Object>> items = new ArrayList<>();
        service(items, "gateway-service", env("GATEWAY_SERVICE_URL", "http://gateway-service:8080"), "/actuator/health", "API Gateway");
        service(items, "auth-service", env("AUTH_SERVICE_URL", "http://auth-service:8081"), "/actuator/health", "Identity API");
        service(items, "task-service", env("TASK_SERVICE_URL", "http://task-service:8082"), "/actuator/health", "Task API");
        service(items, "notification-service", env("NOTIFICATION_SERVICE_URL", "http://notification-service:8083"), "/actuator/health", "Notification API");
        service(items, "release-orchestrator-service", env("RELEASE_ORCHESTRATOR_SERVICE_URL", "http://release-orchestrator-service:8098"), "/actuator/health", "Release lifecycle");
        service(items, "policy-governance-service", env("POLICY_GOVERNANCE_SERVICE_URL", "http://policy-governance-service:8100"), "/actuator/health", "Policy/OPA workflow");
        service(items, "audit-service", env("AUDIT_SERVICE_URL", "http://audit-service:8101"), "/actuator/health", "Audit trail");
        service(items, "keycloak", env("KEYCLOAK_URL", "http://keycloak:8080"), "/realms/master", "Identity provider");
        service(items, "rabbitmq", env("RABBITMQ_HTTP_URL", "http://rabbitmq:15672"), "/api/overview", "Event broker");
        service(items, "redis", env("REDIS_COMMANDER_URL", "http://redis-commander:8081"), "/", "Cache explorer");
        service(items, "mongodb", env("MONGO_EXPRESS_URL", "http://mongo-express:8081"), "/", "Document database explorer");
        service(items, "prometheus", env("PROMETHEUS_URL", "http://prometheus:9090"), "/-/ready", "Metrics store");
        service(items, "grafana", env("GRAFANA_URL", "http://grafana:3000"), "/api/health", "Visualization");
        service(items, "opa", env("OPA_URL", "http://opa:8181"), "/health", "Policy decision engine");
        service(items, "gitlab", env("GITLAB_URL", "http://gitlab:80"), "/-/readiness", "Source and pipelines");
        service(items, "argocd", env("ARGOCD_URL", "http://argocd-server.argocd.svc.cluster.local"), "/api/version", "GitOps control plane");
        Map<String, Object> out = base("platform-services");
        out.put("items", items);
        out.put("up", items.stream().filter(i -> Boolean.TRUE.equals(i.get("live"))).count());
        out.put("down", items.stream().filter(i -> !Boolean.TRUE.equals(i.get("live"))).count());
        return out;
    }

    @GetMapping("/ports")
    public Map<String, Object> ports() {
        Map<String, Object> containers = docker.containers();
        List<Object> items = new ArrayList<>();
        Object raw = containers.get("items");
        if (raw instanceof List<?> list) {
            for (Object o : list) if (o instanceof Map<?,?> m) {
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("id", String.valueOf(m.get("Id") == null ? "" : m.get("Id")));
                row.put("name", firstName(m.get("Names")));
                row.put("image", m.get("Image"));
                row.put("status", m.get("Status"));
                row.put("ports", m.get("Ports"));
                items.add(row);
            }
        }
        Map<String, Object> out = base("runtime-ports");
        out.put("live", Boolean.TRUE.equals(containers.get("live")));
        out.put("items", items);
        out.put("toolStatus", containers.get("toolStatus"));
        return out;
    }

    @GetMapping("/reverse-proxy")
    public Map<String, Object> reverseProxy() {
        List<Map<String,Object>> routes = new ArrayList<>();
        route(routes, "gateway", env("PUBLIC_ORIGIN", "http://localhost:8080"), "/actuator/health");
        route(routes, "grafana", env("GRAFANA_URL", "http://grafana:3000"), "/api/health");
        route(routes, "rabbitmq", env("RABBITMQ_HTTP_URL", "http://rabbitmq:15672"), "/api/overview");
        route(routes, "keycloak", env("KEYCLOAK_URL", "http://keycloak:8080"), "/realms/master");
        route(routes, "prometheus", env("PROMETHEUS_URL", "http://prometheus:9090"), "/-/ready");
        route(routes, "opa", env("OPA_URL", "http://opa:8181"), "/health");
        route(routes, "gitlab", env("GITLAB_URL", "http://gitlab:80"), "/-/readiness");
        route(routes, "argocd", env("ARGOCD_URL", "http://argocd-server.argocd.svc.cluster.local"), "/api/version");
        Map<String,Object> out = base("reverse-proxy");
        out.put("items", routes);
        return out;
    }

    @GetMapping("/keycloak") public Map<String,Object> keycloak() { return probe("keycloak", env("KEYCLOAK_URL", "http://keycloak:8080"), "/realms/master"); }
    @GetMapping("/rabbitmq") public Map<String,Object> rabbitmq() { return probe("rabbitmq", env("RABBITMQ_HTTP_URL", "http://rabbitmq:15672"), "/api/overview"); }
    @GetMapping("/redis") public Map<String,Object> redis() { return probe("redis-commander", env("REDIS_COMMANDER_URL", "http://redis-commander:8081"), "/"); }
    @GetMapping("/mongodb") public Map<String,Object> mongodb() { return probe("mongo-express", env("MONGO_EXPRESS_URL", "http://mongo-express:8081"), "/"); }
    @GetMapping("/prometheus") public Map<String,Object> prometheus() { return probe("prometheus", env("PROMETHEUS_URL", "http://prometheus:9090"), "/-/ready"); }
    @GetMapping("/grafana") public Map<String,Object> grafana() { return probe("grafana", env("GRAFANA_URL", "http://grafana:3000"), "/api/health"); }
    @GetMapping("/opa") public Map<String,Object> opa() { return probe("opa", env("OPA_URL", "http://opa:8181"), "/health"); }
    @GetMapping("/gitlab") public Map<String,Object> gitlab() { return probe("gitlab", env("GITLAB_URL", "http://gitlab:80"), "/-/readiness"); }
    @GetMapping("/argocd") public Map<String,Object> argocd() { return probe("argocd", env("ARGOCD_URL", "http://argocd-server.argocd.svc.cluster.local"), "/api/version"); }
    @GetMapping("/observability") public Map<String,Object> observability() { return observability.stack(); }

    private void service(List<Map<String,Object>> items, String id, String baseUrl, String path, String role) {
        Map<String,Object> row = probe(id, baseUrl, path);
        row.put("role", role);
        items.add(row);
    }
    private void route(List<Map<String,Object>> items, String id, String baseUrl, String path) { items.add(probe(id, baseUrl, path)); }

    private Map<String,Object> probe(String id, String baseUrl, String path) {
        long started = System.nanoTime();
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("name", id);
        out.put("endpoint", baseUrl);
        out.put("path", path);
        out.put("checkedAt", Instant.now().toString());
        out.put("realDataOnly", true);
        try {
            ResponseEntity<String> response = rest.getForEntity(baseUrl.replaceAll("/+$", "") + path, String.class);
            out.put("live", response.getStatusCode().value() < 500);
            out.put("status", response.getStatusCode().value());
            out.put("body", response.getBody());
        } catch (Exception e) {
            out.put("live", false);
            out.put("status", 0);
            out.put("state", "UNREACHABLE");
            out.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        out.put("durationMs", (System.nanoTime() - started) / 1_000_000);
        return out;
    }

    private Map<String,Object> base(String source) {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("source", source);
        out.put("executedAt", Instant.now().toString());
        out.put("realDataOnly", true);
        return out;
    }
    private String env(String key, String fallback) { return System.getenv().getOrDefault(key, fallback); }
    private String firstName(Object names) {
        if (names instanceof List<?> list && !list.isEmpty()) return String.valueOf(list.get(0)).replaceFirst("^/+", "");
        return "-";
    }
}
