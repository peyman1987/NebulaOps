package dev.nebulaops.environment.service;

import dev.nebulaops.environment.client.JsonAdapter;
import dev.nebulaops.environment.client.ProcessExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class EnvironmentService {
    private final ProcessExecutor exec;
    private final JsonAdapter json;

    public EnvironmentService(ProcessExecutor exec, JsonAdapter json) {
        this.exec = exec;
        this.json = json;
    }

    public Map<String, Object> namespaces() {
        return kubectl("kubectl get namespaces -o json");
    }

    public Map<String, Object> namespace(String name) {
        return kubectl("kubectl get namespace " + safe(name) + " -o json");
    }

    public Map<String, Object> pods(String name) {
        return kubectl("kubectl get pods -n " + safe(name) + " -o json");
    }

    private Map<String, Object> kubectl(String cmd) {
        var r = exec.shell(cmd, 30);
        Object data = List.of();
        if (r.ok() && !r.stdout().isBlank()) {
            try {
                data = json.parse(r.stdout());
            } catch (Exception e) {
                data = Map.of("raw", r.stdout(), "parseError", e.getMessage());
            }
        }
        return Map.of("live", r.ok(), "tool", "kubectl", "data", data, "toolStatus", r.status());
    }

    private String safe(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9_.:-]", "");
    }
}
