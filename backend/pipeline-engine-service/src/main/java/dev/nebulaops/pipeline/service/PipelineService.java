package dev.nebulaops.pipeline.service;

import dev.nebulaops.pipeline.client.ProcessExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.time.Instant;
import java.util.Map;

@Service
public class PipelineService {
    private final ProcessExecutor exec;

    public PipelineService(ProcessExecutor exec) {
        this.exec = exec;
    }

    public Map<String, Object> current() {
        Path p = Path.of(System.getenv().getOrDefault("PIPELINE_FILE", "/workspace/.gitlab-ci.yml"));
        try {
            return Map.of("live", Files.exists(p), "tool", "filesystem", "path", p.toString(), "yaml", Files.exists(p) ? Files.readString(p) : "", "executedAt", Instant.now().toString());
        } catch (Exception e) {
            return Map.of("live", false, "tool", "filesystem", "path", p.toString(), "error", e.getMessage(), "executedAt", Instant.now().toString());
        }
    }

    public Map<String, Object> gitStatus() {
        var r = exec.shell("git status --short && git rev-parse --short HEAD", 20);
        return Map.of("live", r.ok(), "tool", "git", "data", r.stdout(), "toolStatus", r.status());
    }

    public Map<String, Object> argoSync(String app) {
        var r = exec.shell("argocd app sync " + safe(app) + " -o json", 60);
        return Map.of("live", r.ok(), "tool", "argocd", "data", r.stdout(), "toolStatus", r.status());
    }

    private String safe(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9_.:/@-]", "");
    }
}
