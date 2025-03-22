package dev.nebulaops.gateway.service;

import dev.nebulaops.gateway.client.JsonToolAdapter;
import dev.nebulaops.gateway.client.ToolCommandClient;
import dev.nebulaops.gateway.client.ToolResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DockerRuntimeService {
    private final ToolCommandClient tools;
    private final JsonToolAdapter json;

    public DockerRuntimeService(ToolCommandClient tools, JsonToolAdapter json) {
        this.tools = tools;
        this.json = json;
    }

    public Map<String, Object> containers() {
        return jsonLines("docker", "docker ps -a --no-trunc --format '{{json .}}'");
    }

    public Map<String, Object> images() {
        return jsonLines("docker", "docker images --no-trunc --format '{{json .}}'");
    }

    public Map<String, Object> volumes() {
        return jsonLines("docker", "docker volume ls --format '{{json .}}'");
    }

    public Map<String, Object> networks() {
        return jsonLines("docker", "docker network ls --format '{{json .}}'");
    }

    public Map<String, Object> builds() {
        return jsonLines("docker", "docker buildx ls --format '{{json .}}'");
    }

    public Map<String, Object> scout() {
        return jsonLines("docker", "docker scout quickview --format json 2>/dev/null || docker scout version --format json 2>/dev/null");
    }

    public Map<String, Object> events() {
        return lines("docker", "docker events --since 10m --until 0s --format '{{json .}}' | tail -50");
    }

    public Map<String, Object> stats() {
        return jsonLines("docker", "docker stats --no-stream --format '{{json .}}'");
    }

    private Map<String, Object> jsonLines(String tool, String command) {
        ToolResult r = tools.shell(command, 12);
        return Map.of("live", r.ok(), "tool", tool, "items", r.ok() ? json.parseJsonLines(r.stdout()) : List.of(), "toolStatus", r.asMap());
    }

    private Map<String, Object> lines(String tool, String command) {
        ToolResult r = tools.shell(command, 12);
        List<String> items = r.ok() && !r.stdout().isBlank() ? java.util.Arrays.asList(r.stdout().split("\\R")) : List.of();
        return Map.of("live", r.ok(), "tool", tool, "items", items, "toolStatus", r.asMap());
    }
}
