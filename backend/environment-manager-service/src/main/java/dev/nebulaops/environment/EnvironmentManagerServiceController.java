package dev.nebulaops.environment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/environments")
public class EnvironmentManagerServiceController {
    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping
    public List<Map<String, Object>> environments() {
        ToolResult ns = run(30, "kubectl", "get", "namespaces", "-o", "json");
        if (!ns.ok)
            return List.of(Map.of("live", false, "name", "unavailable", "namespace", "", "workspace", "", "status", ns.message));
        try {
            Map<String, Object> root = mapper.readValue(ns.stdout, new TypeReference<>() {
            });
            List<Map<String, Object>> items = (List<Map<String, Object>>) root.getOrDefault("items", List.of());
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> item : items) {
                Map<String, Object> meta = (Map<String, Object>) item.getOrDefault("metadata", Map.of());
                Map<String, Object> status = (Map<String, Object>) item.getOrDefault("status", Map.of());
                String name = Objects.toString(meta.getOrDefault("name", ""));
                out.add(Map.of("live", true, "name", name.toUpperCase(Locale.ROOT), "namespace", name, "workspace", terraformWorkspace(name), "status", Objects.toString(status.getOrDefault("phase", "Unknown"))));
            }
            return out;
        } catch (Exception e) {
            return List.of(Map.of("live", false, "name", "parse-error", "namespace", "", "workspace", "", "status", e.getMessage()));
        }
    }

    @PostMapping("/{name}/provision")
    public Map<String, Object> provision(@PathVariable String name) {
        ToolResult ws = run(60, "terraform", "workspace", "select", name);
        if (!ws.ok) ws = run(60, "terraform", "workspace", "new", name);
        return Map.of("live", ws.ok, "status", ws.ok ? "workspace-ready" : "failed", "environment", name, "backend", "terraform-cli", "requestedAt", Instant.now().toString(), "toolStatus", ws.message, "stdout", ws.stdout, "stderr", ws.stderr);
    }

    private String terraformWorkspace(String namespace) {
        ToolResult r = run(15, "terraform", "workspace", "list");
        if (!r.ok) return "terraform-unavailable";
        return Arrays.stream(r.stdout.split("\\R")).map(s -> s.replace("*", "").trim()).filter(s -> s.equals(namespace)).findFirst().orElse("not-mapped");
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
