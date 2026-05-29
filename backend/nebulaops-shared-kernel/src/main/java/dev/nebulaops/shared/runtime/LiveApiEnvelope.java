package dev.nebulaops.shared.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Consistent API envelope for live runtime endpoints.
 *
 * @param live true only when the backing runtime source was reached successfully
 * @param state explicit source state for UI rendering
 * @param source backing tool/service such as docker, kubectl, helm or gateway
 * @param data endpoint payload; may be empty when the source is unavailable
 * @param items optional row collection for table-oriented views
 * @param toolStatus raw tool/service status evidence
 * @param errors structured runtime errors; never represented as HTML pages
 * @param correlationId request correlation id
 * @param generatedAt response creation timestamp
 */
public record LiveApiEnvelope<T>(
        boolean live,
        SourceState state,
        String source,
        T data,
        List<?> items,
        Map<String, Object> toolStatus,
        List<Map<String, Object>> errors,
        String correlationId,
        Instant generatedAt
) {
    public static <T> LiveApiEnvelope<T> ready(String source, T data, List<?> items, Map<String, Object> toolStatus, String correlationId) {
        return new LiveApiEnvelope<>(true, SourceState.READY, source, data, items == null ? List.of() : items,
                toolStatus == null ? Map.of() : toolStatus, List.of(), correlationId, Instant.now());
    }

    public static <T> LiveApiEnvelope<T> unavailable(String source, String message, Map<String, Object> toolStatus, String correlationId) {
        return new LiveApiEnvelope<>(false, SourceState.UNAVAILABLE, source, null, List.of(),
                toolStatus == null ? Map.of() : toolStatus,
                List.of(Map.of("code", "SOURCE_UNAVAILABLE", "message", message == null ? "Runtime source unavailable" : message)),
                correlationId, Instant.now());
    }
}
