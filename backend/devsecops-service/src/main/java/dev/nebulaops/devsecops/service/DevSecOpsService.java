package dev.nebulaops.devsecops.service;

import dev.nebulaops.devsecops.client.JsonAdapter;
import dev.nebulaops.devsecops.client.ProcessExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DevSecOpsService {
    private final ProcessExecutor exec;
    private final JsonAdapter json;

    public DevSecOpsService(ProcessExecutor exec, JsonAdapter json) {
        this.exec = exec;
        this.json = json;
    }

    public Map<String, Object> repositoryScan(String path) {
        return jsonTool("trivy", "trivy fs --format json --quiet " + safePath(path), 120);
    }

    public Map<String, Object> imageScan(String image) {
        return jsonTool("trivy", "trivy image --format json --quiet " + safeImage(image), 120);
    }

    public Map<String, Object> secretScan(String path) {
        var r = exec.shell("grep -RIE --exclude-dir=.git --exclude-dir=node_modules '(password|secret|token|apikey|api_key)[[:space:]]*[:=]' " + safePath(path) + " 2>/dev/null", 30);
        return Map.of("live", r.ok(), "tool", "grep", "items", r.ok() && !r.stdout().isBlank() ? java.util.Arrays.asList(r.stdout().split("\\R")) : List.of(), "toolStatus", r.status());
    }

    private Map<String, Object> jsonTool(String tool, String cmd, int timeout) {
        var r = exec.shell(cmd, timeout);
        Object data = List.of();
        if (r.ok() && !r.stdout().isBlank()) {
            try {
                data = json.parse(r.stdout());
            } catch (Exception e) {
                data = Map.of("raw", r.stdout(), "parseError", e.getMessage());
            }
        }
        return Map.of("live", r.ok(), "tool", tool, "data", data, "toolStatus", r.status());
    }

    private String safePath(String s) {
        return (s == null || s.isBlank() ? "." : s).replaceAll("[^A-Za-z0-9_./:-]", "");
    }

    private String safeImage(String s) {
        return (s == null ? "" : s).replaceAll("[^A-Za-z0-9_./:@-]", "");
    }
}
