package dev.nebulaops.shared;

import java.time.Instant;

public record ErrorResponse(String code, String message, String correlationId, Instant timestamp) {
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, CorrelationContext.newId(), Instant.now());
    }
}
