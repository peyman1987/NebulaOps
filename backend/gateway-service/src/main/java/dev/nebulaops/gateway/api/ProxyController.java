package dev.nebulaops.gateway.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * v21.1 — Forwards specific frontend-required endpoints to downstream services.
 * Uses explicit path mappings (no wildcards) to avoid DispatcherServlet conflicts.
 */
@RestController
@RequestMapping("/api")
public class ProxyController {

    private final RestTemplate rest = new RestTemplate();

    @Value("${TASK_SERVICE_URL:http://task-service:8082}")
    private String taskUrl;

    @Value("${AI_OPS_SERVICE_URL:http://ai-ops-service:8085}")
    private String aiOpsUrl;

    // ── Tasks (frontend calls /api/tasks and /api/tasks?organizationId=...) ──

    @GetMapping("/tasks")
    public ResponseEntity<Object> getTasks(
            @RequestParam(required = false) String organizationId) {
        try {
            String url = taskUrl + "/api/tasks";
            if (organizationId != null) url += "?organizationId=" + organizationId;
            return ResponseEntity.ok(rest.getForObject(url, Object.class));
        } catch (ResourceAccessException e) {
            // Service unreachable — return empty list so UI doesn't break
            return ResponseEntity.ok(List.of());
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/tasks")
    public ResponseEntity<Object> createTask(@RequestBody Object body) {
        try {
            return ResponseEntity.ok(
                rest.postForObject(taskUrl + "/api/tasks", body, Object.class));
        } catch (ResourceAccessException e) {
            return ResponseEntity.ok(Map.of("error", "task-service unavailable"));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<Object> updateTask(@PathVariable String id, @RequestBody Object body) {
        try {
            rest.put(taskUrl + "/api/tasks/" + id, body);
            return ResponseEntity.ok(Map.of("updated", id));
        } catch (ResourceAccessException e) {
            return ResponseEntity.ok(Map.of("error", "task-service unavailable"));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Object> deleteTask(@PathVariable String id) {
        try {
            rest.delete(taskUrl + "/api/tasks/" + id);
            return ResponseEntity.ok(Map.of("deleted", id));
        } catch (ResourceAccessException e) {
            return ResponseEntity.ok(Map.of("error", "task-service unavailable"));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getMessage()));
        }
    }

    // ── AI Ops (frontend calls /api/ai-ops/analyze and /api/ai-ops/autofix) ──

    @PostMapping("/ai-ops/analyze")
    public ResponseEntity<Object> analyze(@RequestBody Object body) {
        try {
            return ResponseEntity.ok(
                rest.postForObject(aiOpsUrl + "/api/ai-ops/analyze", body, Object.class));
        } catch (ResourceAccessException e) {
            return ResponseEntity.ok(Map.of(
                "analysis", "AI Ops service unavailable",
                "suggestions", List.of(),
                "live", false));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/ai-ops/autofix")
    public ResponseEntity<Object> autofix(@RequestBody Object body) {
        try {
            return ResponseEntity.ok(
                rest.postForObject(aiOpsUrl + "/api/ai-ops/autofix", body, Object.class));
        } catch (ResourceAccessException e) {
            return ResponseEntity.ok(Map.of(
                "result", "AI Ops service unavailable",
                "live", false));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getMessage()));
        }
    }
}
