package dev.nebulaops.gateway.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * v22.2 — Gateway proxy for endpoints not directly served by the gateway.
 *
 * Improvements over v22.2:
 *  - Added proxy routes for: /cost/**, /notifications/**, /audit/**, /secrets/**, /registry/**
 *  - Added pipeline/runs forward with pagination support
 *  - All proxy targets wired from application.yml (single source of truth)
 *  - Consistent graceful fallback: empty lists / error maps, never 500
 *  - PATCH forwarding uses HttpEntity to respect Content-Type
 */
@RestController
@RequestMapping("/api")
public class ProxyController {

    private final RestTemplate rest;

    public ProxyController(RestTemplate rest) {
        this.rest = rest;
    }

    @Value("${proxy.task}")           private String taskUrl;
    @Value("${proxy.ai-ops}")         private String aiOpsUrl;
    @Value("${proxy.auth}")           private String authUrl;
    @Value("${proxy.pipeline}")       private String pipelineUrl;
    @Value("${proxy.notification}")   private String notificationUrl;
    @Value("${proxy.cost}")           private String costUrl;
    @Value("${proxy.observability}")  private String observabilityUrl;
    @Value("${proxy.gitops}")         private String gitopsUrl;
    @Value("${proxy.environment}")    private String environmentUrl;
    @Value("${proxy.devsecops}")      private String devsecopsUrl;

    // ── Auth proxy ────────────────────────────────────────────────────────────

    @PostMapping("/auth/login")
    public ResponseEntity<Object> login(@RequestBody Object body) {
        return forward(() -> rest.postForObject(authUrl + "/api/auth/login", body, Object.class),
                       Map.of("error", "auth-service unavailable"));
    }

    @PostMapping("/auth/register")
    public ResponseEntity<Object> register(@RequestBody Object body) {
        return forward(() -> rest.postForObject(authUrl + "/api/auth/register", body, Object.class),
                       Map.of("error", "auth-service unavailable"));
    }

    // ── Tasks proxy ───────────────────────────────────────────────────────────

    @GetMapping("/tasks")
    public ResponseEntity<Object> listTasks(
            @RequestParam(required = false) String organizationId) {
        String url = taskUrl + "/api/tasks"
                   + (organizationId != null ? "?organizationId=" + organizationId : "");
        return forward(() -> rest.getForObject(url, Object.class), List.of());
    }

    @PostMapping("/tasks")
    public ResponseEntity<Object> createTask(@RequestBody Object body) {
        return forward(() -> rest.postForObject(taskUrl + "/api/tasks", body, Object.class),
                       Map.of("error", "task-service unavailable"));
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<Object> updateTask(@PathVariable String id, @RequestBody Object body) {
        return forward(() -> {
            rest.put(taskUrl + "/api/tasks/" + id, body);
            return Map.of("updated", id);
        }, Map.of("error", "task-service unavailable"));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Object> deleteTask(@PathVariable String id) {
        return forward(() -> {
            rest.delete(taskUrl + "/api/tasks/" + id);
            return Map.of("deleted", id);
        }, Map.of("error", "task-service unavailable"));
    }

    @PatchMapping("/tasks/{id}/status/{status}")
    public ResponseEntity<Object> updateStatus(@PathVariable String id, @PathVariable String status) {
        return forward(() -> {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            return rest.exchange(taskUrl + "/api/tasks/" + id + "/status/" + status,
                                 HttpMethod.PATCH,
                                 new HttpEntity<>("{}", h),
                                 Object.class).getBody();
        }, Map.of("error", "task-service unavailable"));
    }

    // ── AI Ops proxy ──────────────────────────────────────────────────────────

    @PostMapping("/ai-ops/analyze")
    public ResponseEntity<Object> analyze(@RequestBody(required = false) Object body) {
        return forward(() -> rest.postForObject(aiOpsUrl + "/api/ai-ops/analyze",
                                                body == null ? Map.of() : body, Object.class),
                       Map.of("analysis", "ai-ops-service unavailable",
                              "suggestions", List.of(), "live", false));
    }

    @PostMapping("/ai-ops/autofix")
    public ResponseEntity<Object> autofix(@RequestBody(required = false) Object body) {
        return forward(() -> rest.postForObject(aiOpsUrl + "/api/ai-ops/autofix",
                                                body == null ? Map.of() : body, Object.class),
                       Map.of("result", "ai-ops-service unavailable", "live", false));
    }

    // ── Pipeline proxy ────────────────────────────────────────────────────────

    @GetMapping("/pipeline/runs")
    public ResponseEntity<Object> pipelineRuns(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return forward(() -> rest.getForObject(
                pipelineUrl + "/api/pipeline/runs?page=" + page + "&size=" + size,
                Object.class), List.of());
    }

    @PostMapping("/pipeline/runs/{id}/trigger")
    public ResponseEntity<Object> triggerPipeline(@PathVariable String id) {
        return forward(() -> rest.postForObject(
                pipelineUrl + "/api/pipeline/runs/" + id + "/trigger", Map.of(), Object.class),
                Map.of("error", "pipeline-service unavailable"));
    }

    // ── Notifications proxy ───────────────────────────────────────────────────

    @GetMapping("/notifications/live")
    public ResponseEntity<Object> notificationsLive(
            @RequestParam(defaultValue = "50") int limit) {
        return forward(() -> rest.getForObject(
                notificationUrl + "/api/notifications?limit=" + limit, Object.class), List.of());
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Object> markRead(@PathVariable String id) {
        return forward(() -> {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            return rest.exchange(notificationUrl + "/api/notifications/" + id + "/read",
                                 HttpMethod.PATCH, new HttpEntity<>("{}", h), Object.class).getBody();
        }, Map.of("error", "notification-service unavailable"));
    }

    // ── Cost analytics proxy ──────────────────────────────────────────────────

    @GetMapping("/cost/summary")
    public ResponseEntity<Object> costSummary(
            @RequestParam(defaultValue = "monthly") String period) {
        return forward(() -> rest.getForObject(
                costUrl + "/api/cost/summary?period=" + period, Object.class),
                Map.of("monthly", 20, "delta", 0, "currency", "EUR",
                       "breakdown", List.of(), "live", false));
    }

    // ── Audit proxy ───────────────────────────────────────────────────────────

    @GetMapping("/audit/events")
    public ResponseEntity<Object> auditEvents(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false)     String actor,
            @RequestParam(required = false)     String action) {
        StringBuilder url = new StringBuilder(observabilityUrl + "/api/audit/events?limit=" + limit);
        if (actor  != null) url.append("&actor=").append(actor);
        if (action != null) url.append("&action=").append(action);
        return forward(() -> rest.getForObject(url.toString(), Object.class), List.of());
    }

    // ── Secrets proxy ─────────────────────────────────────────────────────────

    @GetMapping("/secrets/list")
    public ResponseEntity<Object> secretsList(
            @RequestParam(required = false) String namespace) {
        String url = devsecopsUrl + "/api/secrets"
                   + (namespace != null ? "?namespace=" + namespace : "");
        return forward(() -> rest.getForObject(url, Object.class), List.of());
    }

    // ── Registry proxy ────────────────────────────────────────────────────────

    @GetMapping("/registry/images")
    public ResponseEntity<Object> registryImages(
            @RequestParam(required = false) String repository) {
        String url = devsecopsUrl + "/api/registry/images"
                   + (repository != null ? "?repository=" + repository : "");
        return forward(() -> rest.getForObject(url, Object.class), List.of());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    @FunctionalInterface
    interface Call { Object run() throws Exception; }

    private ResponseEntity<Object> forward(Call call, Object fallback) {
        try {
            Object result = call.run();
            return ResponseEntity.ok(result != null ? result : fallback);
        } catch (ResourceAccessException e) {
            return ResponseEntity.ok(fallback);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode())
                                 .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(fallback);
        }
    }
}
