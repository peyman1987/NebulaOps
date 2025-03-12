package dev.nebulaops.aiops;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
            Map body = engine.post().uri("/analyze").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(Map.class);
            if (body != null) {
                body.put("live", true);
                body.put("source", "ai-engine");
                return body;
            }
        } catch (Exception ignored) {
        }
        return liveDiagnostics(request);
    }

    @PostMapping("/autofix")
    public Map<String, Object> autofix(@RequestBody Map<String, Object> request) {
        String namespace = Objects.toString(request.getOrDefault("namespace", "default"));
        String deployment = Objects.toString(request.getOrDefault("deployment", ""));
        String action = Objects.toString(request.getOrDefault("action", "rollout-restart"));
        if (deployment.isBlank())
            return Map.of("live", false, "status", "rejected", "reason", "deployment is required", "requestedAt", Instant.now().toString());
        ToolResult result = "rollout-restart".equals(action)
                ? run(60, "kubectl", "rollout", "restart", "deployment/" + deployment, "-n", namespace)
                : run(60, "kubectl", "rollout", "status", "deployment/" + deployment, "-n", namespace);
        return Map.of("live", result.ok, "status", result.ok ? "applied" : "failed", "deployment", deployment, "namespace", namespace, "action", action, "appliedAt", Instant.now().toString(), "toolStatus", result.message, "stdout", result.stdout, "stderr", result.stderr);
    }

    @GetMapping("/playbook")
    public Map<String, Object> playbook() {
        return Map.of("version", "20.5-real-backend", "live", true, "features", List.of(
                Map.of("name", "log-analysis", "source", "kubectl logs / docker logs"),
                Map.of("name", "anomaly-detection", "source", "Prometheus/AI engine when available"),
                Map.of("name", "kubernetes-explain", "source", "kubectl explain"),
                Map.of("name", "auto-fix", "source", "kubectl controlled actions")
        ));
    }

    private Map<String, Object> liveDiagnostics(Map<String, Object> request) {
        String namespace = Objects.toString(request.getOrDefault("namespace", "default"));
        ToolResult pods = run(30, "kubectl", "get", "pods", "-n", namespace, "-o", "wide");
        ToolResult events = run(30, "kubectl", "get", "events", "-n", namespace, "--sort-by=.lastTimestamp");
        ToolResult docker = run(30, "sh", "-lc", "docker ps --format '{{.Names}} {{.Status}}' | head -30");
        List<Map<String, Object>> incidentEvents = new ArrayList<>();
        if (!events.stdout.isBlank())
            for (String line : events.stdout.split("\\R")) incidentEvents.add(Map.of("raw", line));
        List<Map<String, Object>> nodes = new ArrayList<>();
        if (!pods.stdout.isBlank())
            for (String line : pods.stdout.split("\\R")) nodes.add(Map.of("raw", line, "source", "kubectl"));
        if (!docker.stdout.isBlank())
            for (String line : docker.stdout.split("\\R")) nodes.add(Map.of("raw", line, "source", "docker"));
        return Map.of(
                "live", pods.ok || events.ok || docker.ok,
                "incidentId", "AIOPS-LIVE-" + System.currentTimeMillis(),
                "summary", "AI engine unavailable; returning real runtime diagnostics instead of mock analysis.",
                "rootCause", "requires operator/AI review of live events and logs",
                "confidence", null,
                "fix", "No automatic fix generated without AI engine response.",
                "events", incidentEvents,
                "nodes", nodes,
                "toolStatus", Map.of("kubectlPods", pods.message, "kubectlEvents", events.message, "docker", docker.message)
        );
    }

    private ToolResult run(int timeout, String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).start();
            boolean done = p.waitFor(timeout, TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!done) {
                p.destroyForcibly();
                return new ToolResult(false, out, err, "timeout: " + String.join(" ", cmd));
            }
            return new ToolResult(p.exitValue() == 0, out, err, String.join(" ", cmd) + " exit=" + p.exitValue());
        } catch (Exception e) {
            return new ToolResult(false, "", e.getMessage(), "tool unavailable: " + cmd[0] + " - " + e.getMessage());
        }
    }

    record ToolResult(boolean ok, String stdout, String stderr, String message) {
    }
}
