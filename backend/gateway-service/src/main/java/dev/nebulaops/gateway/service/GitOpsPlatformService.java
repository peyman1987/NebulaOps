package dev.nebulaops.gateway.service;

import dev.nebulaops.gateway.client.JsonToolAdapter;
import dev.nebulaops.gateway.client.ToolCommandClient;
import dev.nebulaops.gateway.client.ToolResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class GitOpsPlatformService {
    private final ToolCommandClient tools;
    private final JsonToolAdapter json;

    public GitOpsPlatformService(ToolCommandClient tools, JsonToolAdapter json) {
        this.tools = tools;
        this.json = json;
    }

    public Map<String, Object> applications() {
        return command("argocd", "argocd app list -o json");
    }

    public Map<String, Object> application(String app) {
        return command("argocd", "argocd app get " + safe(app) + " -o json");
    }

    public Map<String, Object> sync(String app) {
        return command("argocd", "argocd app sync " + safe(app) + " -o json");
    }

    public Map<String, Object> rollback(String app, String revision) {
        return command("argocd", "argocd app rollback " + safe(app) + " " + safe(revision) + " -o json");
    }

    private Map<String, Object> command(String tool, String command) {
        ToolResult r = tools.shell(command, 25);
        Object data = List.of();
        if (r.ok() && !r.stdout().isBlank()) {
            try {
                data = json.parseJson(r.stdout());
            } catch (Exception e) {
                data = Map.of("raw", r.stdout(), "parseError", e.getMessage());
            }
        }
        return Map.of("live", r.ok(), "tool", tool, "data", data, "toolStatus", r.asMap());
    }

    private String safe(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9_.:/@-]", "");
    }
}
