package dev.nebulaops.gateway.service;

import dev.nebulaops.gateway.client.DockerSocketClient;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * v22.4 — Docker runtime data via DockerSocketClient (Unix socket HTTP).
 * Bypasses docker CLI entirely — works regardless of snap/wrapper binary issues.
 * Returns {live, tool, items} map shape consumed by RuntimeOpsController.
 */
@Service
public class DockerRuntimeService {

    private final DockerSocketClient socket;

    public DockerRuntimeService(DockerSocketClient socket) {
        this.socket = socket;
    }

    public Map<String, Object> containers() {
        List<Map<String, Object>> items = socket.containers();
        return Map.of("live", !items.isEmpty(), "tool", "docker-socket", "items", items,
                      "toolStatus", Map.of("ok", !items.isEmpty()));
    }

    public Map<String, Object> images() {
        List<Map<String, Object>> items = socket.images();
        return Map.of("live", !items.isEmpty(), "tool", "docker-socket", "items", items,
                      "toolStatus", Map.of("ok", !items.isEmpty()));
    }

    public Map<String, Object> volumes() {
        List<Map<String, Object>> items = socket.volumes();
        return Map.of("live", !items.isEmpty(), "tool", "docker-socket", "items", items,
                      "toolStatus", Map.of("ok", !items.isEmpty()));
    }

    public Map<String, Object> networks() {
        List<Map<String, Object>> items = socket.networks();
        return Map.of("live", !items.isEmpty(), "tool", "docker-socket", "items", items,
                      "toolStatus", Map.of("ok", !items.isEmpty()));
    }

    public Map<String, Object> stats()  { return Map.of("live", false, "items", List.of()); }
    public Map<String, Object> builds() { return Map.of("live", false, "items", List.of()); }
    public Map<String, Object> scout()  { return Map.of("live", false, "items", List.of()); }
    public Map<String, Object> events() { return Map.of("live", false, "items", List.of()); }
}
