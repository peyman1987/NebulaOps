package dev.nebulaops.pipeline;

import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

@RestController
@RequestMapping("/api/pipeline-engine")
public class PipelineEngineController {
    private final Map<String, Map<String, Object>> designs = new ConcurrentHashMap<>();
    private final Path repo = Paths.get(System.getenv().getOrDefault("REPO_PATH", "/workspace"));

    @GetMapping("/template")
    public Map<String, Object> template() {
        Path gitlab = repo.resolve(".gitlab-ci.yml");
        String yaml = read(gitlab);
        ToolResult git = run(repo, 10, "git", "rev-parse", "--short", "HEAD");
        return Map.of(
                "version", "20.5-real-backend",
                "live", Files.exists(gitlab),
                "source", gitlab.toString(),
                "stages", extractStages(yaml),
                "jobs", extractJobs(yaml),
                "gitlab", yaml,
                "revision", git.ok ? git.stdout.trim() : "unknown",
                "generatedAt", Instant.now().toString(),
                "toolStatus", git.message
        );
    }

    @PostMapping("/designs")
    public Map<String, Object> saveDesign(@RequestBody Map<String, Object> design) {
        String id = Objects.toString(design.getOrDefault("id", "PIPE-" + System.currentTimeMillis()));
        Map<String, Object> saved = new LinkedHashMap<>(design);
        saved.put("id", id);
        saved.put("savedAt", Instant.now().toString());
        saved.put("source", "user-submitted");
        designs.put(id, saved);
        return Map.of("status", "saved", "id", id, "design", saved);
    }

    @GetMapping("/designs")
    public Collection<Map<String, Object>> listDesigns() {
        return designs.values();
    }

    @PostMapping("/gitlab/export")
    public Map<String, Object> exportGitLab(@RequestBody(required = false) Map<String, Object> body) {
        Path gitlab = repo.resolve(".gitlab-ci.yml");
        String yaml = body != null && body.get("yaml") != null ? Objects.toString(body.get("yaml")) : read(gitlab);
        return Map.of("filename", ".gitlab-ci.yml", "yaml", yaml, "source", body != null && body.get("yaml") != null ? "request-body" : gitlab.toString(), "generatedAt", Instant.now().toString());
    }

    @PostMapping("/argocd/sync")
    public Map<String, Object> syncArgoCd(@RequestBody(required = false) Map<String, Object> body) {
        String app = Objects.toString(body == null ? "nebulaops" : body.getOrDefault("application", "nebulaops"));
        ToolResult res = run(repo, 120, "argocd", "app", "sync", app);
        return Map.of("live", res.ok, "status", res.ok ? "sync-completed" : "sync-failed", "application", app, "revision", currentRevision(), "requestedAt", Instant.now().toString(), "toolStatus", res.message, "stdout", res.stdout, "stderr", res.stderr);
    }

    private String currentRevision() {
        ToolResult r = run(repo, 10, "git", "rev-parse", "--short", "HEAD");
        return r.ok ? r.stdout.trim() : "unknown";
    }

    private String read(Path p) {
        try {
            return Files.exists(p) ? Files.readString(p) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private List<String> extractStages(String yaml) {
        List<String> out = new ArrayList<>();
        boolean in = false;
        for (String line : yaml.split("\\R")) {
            if (line.trim().equals("stages:")) {
                in = true;
                continue;
            }
            if (in && line.matches("\\s*-\\s+.+")) out.add(line.replaceFirst("\\s*-\\s+", "").trim());
            else if (in && !line.isBlank() && !line.startsWith(" ")) break;
        }
        return out;
    }

    private List<Map<String, Object>> extractJobs(String yaml) {
        List<Map<String, Object>> out = new ArrayList<>();
        Pattern job = Pattern.compile("^([A-Za-z0-9_.-]+):\\s*$");
        String current = null, stage = "";
        for (String line : yaml.split("\\R")) {
            Matcher m = job.matcher(line);
            if (m.matches() && !m.group(1).equals("stages") && !m.group(1).startsWith(".")) {
                if (current != null) out.add(Map.of("name", current, "stage", stage));
                current = m.group(1);
                stage = "";
            } else if (current != null && line.trim().startsWith("stage:"))
                stage = line.substring(line.indexOf(':') + 1).trim();
        }
        if (current != null) out.add(Map.of("name", current, "stage", stage));
        return out;
    }

    private ToolResult run(Path cwd, int timeout, String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(cwd.toFile());
            Process p = pb.start();
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
