package dev.nebulaops.gateway.client;

import java.util.Map;

public record ToolResult(boolean ok, int exitCode, String message, String stdout, String stderr, long durationMs,
                         String executedAt) {
    public Map<String, Object> asMap() {
        return Map.of("ok", ok, "exitCode", exitCode, "message", message, "stderr", stderr == null ? "" : stderr, "durationMs", durationMs, "executedAt", executedAt);
    }
}
