package dev.nebulaops.gitops.service;

import dev.nebulaops.gitops.client.JsonAdapter;
import dev.nebulaops.gitops.client.ProcessExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class GitOpsService {
    private final ProcessExecutor exec;
    private final JsonAdapter json;

    public GitOpsService(ProcessExecutor exec, JsonAdapter json) {
        this.exec = exec;
        this.json = json;
    }

    public Map<String, Object> apps() {
        return argocd("argocd app list -o json");
    }

    public Map<String, Object> app(String app) {
        return argocd("argocd app get " + safe(app) + " -o json");
    }

    public Map<String, Object> sync(String app) {
        return argocd("argocd app sync " + safe(app) + " -o json");
    }

    public Map<String, Object> rollback(String app, String revision) {
        return argocd("argocd app rollback " + safe(app) + " " + safe(revision) + " -o json");
    }

    private Map<String, Object> argocd(String cmd) {
        var r = exec.shell(cmd, 60);
        Object data = List.of();
        if (r.ok() && !r.stdout().isBlank()) {
            try {
                data = json.parse(r.stdout());
            } catch (Exception e) {
                data = Map.of("raw", r.stdout(), "parseError", e.getMessage());
            }
        }
        return Map.of("live", r.ok(), "tool", "argocd", "data", data, "toolStatus", r.status());
    }

    private String safe(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9_.:/@-]", "");
    }
}
