package dev.nebulaops.gitops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/gitops")
public class GitOpsControlServiceController {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String app = System.getenv().getOrDefault("ARGOCD_APP", "nebulaops");

    @GetMapping("/state")
    public Map<String, Object> state() {
        ToolResult argo = run(30, "argocd", "app", "get", app, "-o", "json");
        if (argo.ok) {
            try {
                Map<String, Object> root = mapper.readValue(argo.stdout, new TypeReference<>() {
                });
                Map<String, Object> status = (Map<String, Object>) root.getOrDefault("status", Map.of());
                Map<String, Object> sync = (Map<String, Object>) status.getOrDefault("sync", Map.of());
                Map<String, Object> health = (Map<String, Object>) status.getOrDefault("health", Map.of());
                List<Map<String, Object>> resources = (List<Map<String, Object>>) status.getOrDefault("resources", List.of());
                long drift = resources.stream().filter(r -> !"Synced".equals(Objects.toString(r.getOrDefault("status", "")))).count();
                return Map.of("live", true, "tool", "argocd", "application", app, "sync", Objects.toString(sync.getOrDefault("status", "Unknown")), "drift", drift, "revision", Objects.toString(sync.getOrDefault("revision", "")), "health", Objects.toString(health.getOrDefault("status", "Unknown")), "resources", resources, "generatedAt", Instant.now().toString());
            } catch (Exception e) {
                return unavailable("argocd json parse failed: " + e.getMessage(), argo);
            }
        }
        ToolResult kubectl = run(30, "kubectl", "get", "applications.argoproj.io", "-A", "-o", "json");
        if (kubectl.ok)
            return Map.of("live", true, "tool", "kubectl", "sync", "See resources", "drift", 0, "revision", revision(), "health", "ArgoCD CRD reachable", "raw", kubectl.stdout, "generatedAt", Instant.now().toString());
        return unavailable("Neither argocd CLI nor ArgoCD CRDs are available", kubectl);
    }

    @PostMapping("/sync")
    public Map<String, Object> sync(@RequestBody(required = false) Map<String, Object> body) {
        String target = Objects.toString(body == null ? app : body.getOrDefault("application", app));
        ToolResult res = run(120, "argocd", "app", "sync", target);
        return Map.of("live", res.ok, "status", res.ok ? "sync-completed" : "sync-failed", "application", target, "requestedAt", Instant.now().toString(), "toolStatus", res.message, "stdout", res.stdout, "stderr", res.stderr);
    }

    @PostMapping("/rollback")
    public Map<String, Object> rollback(@RequestBody(required = false) Map<String, Object> body) {
        String target = Objects.toString(body == null ? app : body.getOrDefault("application", app));
        String revision = Objects.toString(body == null ? "" : body.getOrDefault("revision", ""));
        ToolResult res = revision.isBlank() ? run(120, "argocd", "app", "rollback", target) : run(120, "argocd", "app", "rollback", target, revision);
        return Map.of("live", res.ok, "status", res.ok ? "rollback-completed" : "rollback-failed", "application", target, "revision", revision, "requestedAt", Instant.now().toString(), "toolStatus", res.message, "stdout", res.stdout, "stderr", res.stderr);
    }

    private Map<String, Object> unavailable(String reason, ToolResult result) {
        return Map.of("live", false, "sync", "Unavailable", "drift", 0, "revision", revision(), "health", reason, "generatedAt", Instant.now().toString(), "toolStatus", result.message);
    }

    private String revision() {
        ToolResult r = run(10, "git", "rev-parse", "--short", "HEAD");
        return r.ok ? r.stdout.trim() : "unknown";
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
