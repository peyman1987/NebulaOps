package dev.nebulaops.tfstudio;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/terraform-studio")
public class TerraformStudioServiceController {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path workdir = Paths.get(System.getenv().getOrDefault("TERRAFORM_WORKDIR", "/workspace/infrastructure/terraform"));

    @GetMapping("/graph")
    public Map<String, Object> graph() {
        ToolResult result = run(workdir, 20, "terraform", "graph");
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        if (result.ok()) {
            parseDot(result.stdout, nodes, edges);
        }
        return livePayload(Map.of(
                "nodes", nodes,
                "edges", edges,
                "mode", "terraform-graph",
                "source", workdir.toString(),
                "command", "terraform graph",
                "raw", result.stdout
        ), result);
    }

    @GetMapping("/plan")
    public Map<String, Object> plan() {
        Path planFile = Paths.get("/tmp/nebulaops-tfplan-" + System.currentTimeMillis());
        ToolResult init = run(workdir, 120, "terraform", "init", "-input=false", "-no-color");
        if (!init.ok()) return livePayload(emptyPlan("terraform init failed"), init);
        ToolResult plan = run(workdir, 180, "terraform", "plan", "-input=false", "-lock=false", "-no-color", "-out=" + planFile);
        if (!plan.ok()) return livePayload(emptyPlan("terraform plan failed"), plan);
        ToolResult show = run(workdir, 60, "terraform", "show", "-json", planFile.toString());
        try {
            Files.deleteIfExists(planFile);
        } catch (Exception ignored) {
        }
        if (!show.ok()) return livePayload(emptyPlan("terraform show failed"), show);
        try {
            Map<String, Object> root = mapper.readValue(show.stdout, new TypeReference<>() {
            });
            List<Map<String, Object>> changes = (List<Map<String, Object>>) root.getOrDefault("resource_changes", List.of());
            int add = 0, change = 0, destroy = 0;
            List<Map<String, Object>> resources = new ArrayList<>();
            for (Map<String, Object> rc : changes) {
                Map<String, Object> ch = (Map<String, Object>) rc.getOrDefault("change", Map.of());
                List<String> actions = ((List<?>) ch.getOrDefault("actions", List.of())).stream().map(Object::toString).toList();
                if (actions.contains("create")) add++;
                if (actions.contains("update")) change++;
                if (actions.contains("delete")) destroy++;
                resources.add(Map.of(
                        "address", Objects.toString(rc.getOrDefault("address", "")),
                        "type", Objects.toString(rc.getOrDefault("type", "")),
                        "name", Objects.toString(rc.getOrDefault("name", "")),
                        "actions", actions
                ));
            }
            return livePayload(Map.of(
                    "add", add,
                    "change", change,
                    "destroy", destroy,
                    "resources", resources,
                    "currency", System.getenv().getOrDefault("COST_CURRENCY", "EUR"),
                    "monthlyCost", null,
                    "costSource", "not-calculated: configure infracost for live cost estimates"
            ), show);
        } catch (Exception e) {
            return livePayload(emptyPlan("cannot parse terraform show json: " + e.getMessage()), show);
        }
    }

    @GetMapping("/modules")
    public Map<String, Object> modules() {
        List<Map<String, Object>> modules = new ArrayList<>();
        Path modulesDir = workdir.resolve("modules");
        try {
            if (Files.isDirectory(modulesDir)) {
                try (var stream = Files.list(modulesDir)) {
                    modules = stream.filter(Files::isDirectory).map(p -> Map.<String, Object>of(
                            "name", p.getFileName().toString(),
                            "path", workdir.relativize(p).toString(),
                            "files", countTfFiles(p)
                    )).collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            return livePayload(Map.of("modules", List.of(), "source", modulesDir.toString()), ToolResult.error("module discovery failed: " + e.getMessage()));
        }
        return livePayload(Map.of("modules", modules, "source", modulesDir.toString()), ToolResult.success("filesystem scan"));
    }

    private Map<String, Object> emptyPlan(String reason) {
        return Map.of("add", 0, "change", 0, "destroy", 0, "resources", List.of(), "monthlyCost", null, "currency", System.getenv().getOrDefault("COST_CURRENCY", "EUR"), "reason", reason);
    }

    private long countTfFiles(Path dir) {
        try (var stream = Files.walk(dir)) {
            return stream.filter(p -> p.toString().endsWith(".tf")).count();
        } catch (Exception e) {
            return 0;
        }
    }

    private void parseDot(String dot, List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Set<String> seen = new LinkedHashSet<>();
        for (String line : dot.split("\\R")) {
            String l = line.trim();
            if (l.contains("->")) {
                String[] parts = l.replace(";", "").replace("\"", "").split("->");
                if (parts.length >= 2) {
                    String from = clean(parts[0]);
                    String to = clean(parts[1]);
                    edges.add(Map.of("from", from, "to", to));
                    seen.add(from);
                    seen.add(to);
                }
            } else if (l.startsWith("\"") && l.endsWith(";")) {
                seen.add(clean(l.replace(";", "").replace("\"", "")));
            }
        }
        for (String n : seen)
            nodes.add(Map.of("id", n, "label", n.substring(n.lastIndexOf('.') + 1), "type", n.contains("module.") ? "module" : "resource"));
    }

    private String clean(String s) {
        return s.replaceAll("\\[.*$", "").trim();
    }

    private Map<String, Object> livePayload(Map<String, Object> data, ToolResult result) {
        Map<String, Object> out = new LinkedHashMap<>(data);
        out.put("live", result.ok());
        out.put("generatedAt", Instant.now().toString());
        out.put("toolStatus", Map.of("ok", result.ok(), "message", result.message(), "stderr", result.stderr));
        return out;
    }

    private ToolResult run(Path cwd, int timeoutSeconds, String... cmd) {
        try {
            if (!Files.isDirectory(cwd)) return ToolResult.error("workdir does not exist: " + cwd);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(cwd.toFile());
            pb.redirectErrorStream(false);
            Process p = pb.start();
            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                p.destroyForcibly();
                return new ToolResult(false, out, err, "timeout running " + String.join(" ", cmd));
            }
            return new ToolResult(p.exitValue() == 0, out, err, String.join(" ", cmd) + " exit=" + p.exitValue());
        } catch (Exception e) {
            return ToolResult.error("cannot run " + String.join(" ", cmd) + ": " + e.getMessage());
        }
    }

    record ToolResult(boolean ok, String stdout, String stderr, String message) {
        static ToolResult error(String msg) {
            return new ToolResult(false, "", msg, msg);
        }

        static ToolResult success(String msg) {
            return new ToolResult(true, "", "", msg);
        }
    }
}
