package dev.nebulaops.gateway.service;

import dev.nebulaops.gateway.client.DockerSocketClient;
import dev.nebulaops.gateway.client.JsonToolAdapter;
import dev.nebulaops.gateway.client.ToolCommandClient;
import dev.nebulaops.gateway.client.ToolResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * v21.2-fixed — Docker runtime service.
 *
 * Uses DockerSocketClient (direct Unix socket HTTP) as the primary source.
 * Falls back to CLI only for operations not available via socket (builds, scout).
 * This bypasses the snap docker binary that doesn't support CLI commands.
 */
@Service
public class DockerRuntimeService {

    private final DockerSocketClient socket;
    private final ToolCommandClient  tools;
    private final JsonToolAdapter    json;

    public DockerRuntimeService(DockerSocketClient socket,
                                ToolCommandClient tools,
                                JsonToolAdapter json) {
        this.socket = socket;
        this.tools  = tools;
        this.json   = json;
    }

    public Map<String, Object> containers() {
        List<Map<String, Object>> items = socket.containers();
        return Map.of("live", !items.isEmpty(), "tool", "docker-socket",
                      "items", items, "toolStatus", Map.of("ok", !items.isEmpty()));
    }

    public Map<String, Object> images() {
        List<Map<String, Object>> items = socket.images();
        return Map.of("live", !items.isEmpty(), "tool", "docker-socket",
                      "items", items, "toolStatus", Map.of("ok", !items.isEmpty()));
    }

    public Map<String, Object> volumes() {
        List<Map<String, Object>> items = socket.volumes();
        return Map.of("live", !items.isEmpty(), "tool", "docker-socket",
                      "items", items, "toolStatus", Map.of("ok", !items.isEmpty()));
    }

    public Map<String, Object> networks() {
        List<Map<String, Object>> items = socket.networks();
        return Map.of("live", !items.isEmpty(), "tool", "docker-socket",
                      "items", items, "toolStatus", Map.of("ok", !items.isEmpty()));
    }

    public Map<String, Object> stats() {
        // Stats not available via simple socket GET without streaming; use CLI fallback
        return jsonLines("docker", "docker stats --no-stream --format '{{json .}}'");
    }

    public Map<String, Object> builds() {
        return jsonLines("docker", "docker buildx ls --format '{{json .}}'");
    }

    public Map<String, Object> scout() {
        return jsonLines("docker", "docker scout quickview --format json 2>/dev/null || echo '[]'");
    }

    public Map<String, Object> events() {
        return lines("docker", "docker events --since 10m --until 0s --format '{{json .}}' 2>/dev/null | tail -50");
    }

    private Map<String, Object> jsonLines(String tool, String command) {
        ToolResult r = tools.shell(command, 12);
        return Map.of("live", r.ok(), "tool", tool,
                      "items", r.ok() ? json.parseJsonLines(r.stdout()) : List.of(),
                      "toolStatus", r.asMap());
    }

    private Map<String, Object> lines(String tool, String command) {
        ToolResult r = tools.shell(command, 12);
        List<String> items = r.ok() && !r.stdout().isBlank()
            ? java.util.Arrays.asList(r.stdout().split("\\R")) : List.of();
        return Map.of("live", r.ok(), "tool", tool, "items", items, "toolStatus", r.asMap());
    }
}
