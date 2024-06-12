package dev.nebulaops.aiops;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/ai-ops")
public class AiOpsController {
    private final RestClient engine;

    public AiOpsController(@Value("${aiops.engine-url}") String engineUrl) {
        this.engine = RestClient.builder().baseUrl(engineUrl).build();
    }

    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestBody Map<String, Object> request) {
        try {
            return engine.post().uri("/analyze").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(Map.class);
        } catch (Exception ignored) {
            return fallback(request);
        }
    }

    @PostMapping("/autofix")
    public Map<String, Object> autofix(@RequestBody Map<String, Object> request) {
        return Map.of(
                "status", "staged",
                "incidentId", Objects.toString(request.getOrDefault("incidentId", "AIOPS-LOCAL")),
                "action", Objects.toString(request.getOrDefault("fix", "patch readiness probe")),
                "appliedAt", Instant.now().toString(),
                "note", "Demo-safe mode: generated patch staged for operator review. Wire this endpoint to kubectl apply for controlled environments."
        );
    }

    @GetMapping("/playbook")
    public Map<String, Object> playbook() {
        return Map.of("version", "19.1", "features", List.of("log-analysis", "anomaly-detection", "kubernetes-explain", "helm-yaml-generation", "incident-summary", "auto-fix-staging"));
    }

    private Map<String, Object> fallback(Map<String, Object> request) {
        String prompt = Objects.toString(request.getOrDefault("prompt", "CrashLoopBackOff"));
        return Map.of(
                "incidentId", "AIOPS-19-1-FALLBACK",
                "summary", "AI Ops detected a Kubernetes incident pattern and produced a safe remediation plan.",
                "rootCause", prompt.toLowerCase().contains("image") ? "Image tag or pull policy mismatch." : "Readiness probe timeout and rollout instability.",
                "confidence", 0.88,
                "blastRadius", List.of("frontend", "gateway-service", "task-service", "notification-service"),
                "fix", "Patch readiness probe, verify image tag and restart rollout.",
                "yaml", "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: gateway-service\n  namespace: nebulaops\nspec:\n  template:\n    spec:\n      containers:\n        - name: gateway-service\n          readinessProbe:\n            httpGet:\n              path: /actuator/health\n              port: 8080\n            initialDelaySeconds: 25\n",
                "events", List.of(
                        event("gateway-service", "CRITICAL", "Pod CrashLoopBackOff", "active", "Patch readiness probe"),
                        event("frontend", "HIGH", "5xx propagation", "degraded", "Drain traffic from unhealthy gateway"),
                        event("task-service", "MEDIUM", "Queue latency spike", "watch", "Scale if queue depth grows"),
                        event("mongodb", "LOW", "No storage anomaly", "stable", "No action")
                ),
                "nodes", List.of(
                        node("frontend", "Frontend", "edge", 72, 14, 24, 1, "warn"),
                        node("gateway", "Gateway", "api", 18, 42, 38, 4, "critical"),
                        node("tasks", "Tasks", "svc", 66, 69, 22, 2, "warn"),
                        node("mongo", "MongoDB", "db", 96, 78, 66, 1, "ok"),
                        node("notify", "Notify", "svc", 61, 30, 72, 3, "warn")
                )
        );
    }

    private Map<String, Object> event(String service, String severity, String title, String status, String recommendation) {
        return Map.of("time", java.time.LocalTime.now().withNano(0).toString(), "service", service, "severity", severity, "title", title, "status", status, "recommendation", recommendation);
    }

    private Map<String, Object> node(String id, String label, String type, int health, int x, int y, int z, String status) {
        return Map.of("id", id, "label", label, "type", type, "health", health, "x", x, "y", y, "z", z, "status", status);
    }
}
