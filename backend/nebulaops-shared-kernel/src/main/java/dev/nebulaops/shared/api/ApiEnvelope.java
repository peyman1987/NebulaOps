package dev.nebulaops.shared.api;

import java.time.Instant;

public record ApiEnvelope<T>(
        boolean success,
        T data,
        String message,
        Instant timestamp
) {
    public static <T> ApiEnvelope<T> ok(T data) {
        return new ApiEnvelope<>(true, data, null, Instant.now());
    }

    public static <T> ApiEnvelope<T> ok(T data, String message) {
        return new ApiEnvelope<>(true, data, message, Instant.now());
    }

    public static <T> ApiEnvelope<T> failed(String message) {
        return new ApiEnvelope<>(false, null, message, Instant.now());
    }
}
