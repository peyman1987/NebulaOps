package dev.nebulaops.shared;

import java.time.Instant;

public record ApiResponse<T>(boolean success, T data, String message, String correlationId, Instant timestamp) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, "ok", CorrelationContext.newId(), Instant.now());
    }
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, null, message, CorrelationContext.newId(), Instant.now());
    }
}
