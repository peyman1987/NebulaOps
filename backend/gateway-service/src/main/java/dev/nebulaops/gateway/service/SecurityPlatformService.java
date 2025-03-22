package dev.nebulaops.gateway.service;

import dev.nebulaops.gateway.client.JsonToolAdapter;
import dev.nebulaops.gateway.client.ToolCommandClient;
import dev.nebulaops.gateway.client.ToolResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SecurityPlatformService {
    private final ToolCommandClient tools;
    private final JsonToolAdapter json;

    public SecurityPlatformService(ToolCommandClient tools, JsonToolAdapter json) {
        this.tools = tools;
        this.json = json;
    }

    public Map<String, Object> trivyFs(String path) {
        return jsonCommand("trivy", "trivy fs --format json --quiet " + safePath(path == null || path.isBlank() ? "." : path));
    }

    public Map<String, Object> trivyImage(String image) {
        return jsonCommand("trivy", "trivy image --format json --quiet " + safeImage(image));
    }

    public Map<String, Object> secretScan(String path) {
        return lines("grep", "grep -RIE --exclude-dir=.git --exclude-dir=node_modules '(password|secret|token|apikey|api_key)[[:space:]]*[:=]' " + safePath(path == null || path.isBlank() ? "." : path) + " 2>/dev/null");
    }

    private Map<String, Object> jsonCommand(String tool, String command) {
        ToolResult r = tools.shell(command, 60);
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

    private Map<String, Object> lines(String tool, String command) {
        ToolResult r = tools.shell(command, 20);
        List<String> items = r.ok() && !r.stdout().isBlank() ? java.util.Arrays.asList(r.stdout().split("\\R")) : List.of();
        return Map.of("live", r.ok(), "tool", tool, "items", items, "toolStatus", r.asMap());
    }

    private String safePath(String s) {
        return s.replaceAll("[^A-Za-z0-9_./:-]", "");
    }

    private String safeImage(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9_./:@-]", "");
    }
}
