package dev.nebulaops.shared.api;

import java.time.Instant;
import java.util.Map;

public record ErrorEnvelope(
        String code,
        String message,
        String path,
        Instant timestamp,
        Map<String, Object> details
) {
    public static ErrorEnvelope of(String code, String message, String path) {
        return new ErrorEnvelope(code, message, path, Instant.now(), Map.of());
    }
}
