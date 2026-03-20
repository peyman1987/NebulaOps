package com.apiforge.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ApiModels {
    public record HeaderKV(String key, String value, boolean enabled) {}
    public record QueryKV(String key, String value, boolean enabled) {}
    public record FormKV(String key, String value, String type, boolean enabled) {}
    public record AuthConfig(String type, Map<String, String> values) {}
    public record ScriptConfig(String preRequest, String tests) {}

    public record HttpRequestDto(
        String id, String name, String method, String url,
        List<HeaderKV> headers, List<QueryKV> queryParams,
        AuthConfig auth, String bodyType, String body,
        List<FormKV> formData, Map<String, String> form,
        ScriptConfig scripts, Integer timeoutSeconds
    ) {}

    public record HttpResponseDto(
        int status, String statusText,
        Map<String, List<String>> headers, String body,
        long timeMs, long sizeBytes,
        List<String> cookies, Instant timestamp
    ) {}

    public record StoredRequest(String id, String name, HttpRequestDto request, Instant createdAt) {}

    public record CollectionDto(
        String id, String name, String description,
        List<StoredRequest> requests,
        Instant createdAt, Instant updatedAt
    ) {}

    public record EnvironmentDto(
        String id, String name, Map<String, String> variables, boolean active
    ) {}

    public record HistoryDto(
        String id, HttpRequestDto request, HttpResponseDto response, Instant timestamp
    ) {}

    public record RunnerRequestDto(
        String collectionId, int delayMs, boolean stopOnError
    ) {}

    public record RunnerResultDto(
        String requestName, String method, String url,
        int status, long timeMs, boolean passed, String error,
        List<TestResult> tests
    ) {}

    public record TestResult(String name, boolean pass, String error) {}

    public record CodegenRequest(String language, HttpRequestDto request) {}
}
