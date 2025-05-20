package dev.nebulaops.gateway.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * v21.2 — Gateway proxy for endpoints not directly served by the gateway.
 * Reads downstream URLs from application.yml (proxy.*), which itself
 * mirrors /config/platform.yml (single source of truth).
 */
@RestController
@RequestMapping("/api")
public class ProxyController {

    private final RestTemplate rest;

    public ProxyController(RestTemplate rest) {
        this.rest = rest;
    }

    @Value("${proxy.task}")
    private String taskUrl;

    @Value("${proxy.ai-ops}")
    private String aiOpsUrl;

    @Value("${proxy.auth}")
    private String authUrl;

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
            // Use exchange + SimpleClientHttpRequestFactory PATCH support
            org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
            h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            return rest.exchange(taskUrl + "/api/tasks/" + id + "/status/" + status,
                                 org.springframework.http.HttpMethod.PATCH,
                                 new org.springframework.http.HttpEntity<>("{}", h),
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

    // ── Helper ────────────────────────────────────────────────────────────────

    @FunctionalInterface
    interface Call {
        Object run() throws Exception;
    }

    private ResponseEntity<Object> forward(Call call, Object fallback) {
        try {
            Object result = call.run();
            return ResponseEntity.ok(result != null ? result : fallback);
        } catch (ResourceAccessException e) {
            return ResponseEntity.ok(fallback);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(fallback);
        }
    }
}
