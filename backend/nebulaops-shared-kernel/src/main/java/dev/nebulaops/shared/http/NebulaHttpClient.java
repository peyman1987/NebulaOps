package dev.nebulaops.shared.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public class NebulaHttpClient {
    private final HttpClient client;
    private final Duration timeout;

    public NebulaHttpClient() {
        this(Duration.ofSeconds(10));
    }

    public NebulaHttpClient(Duration timeout) {
        this.timeout = timeout;
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public HttpResult get(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();
        return send(request);
    }

    public HttpResult postJson(String url, String json) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json == null ? "{}" : json))
                .build();
        return send(request);
    }

    private HttpResult send(HttpRequest request) {
        Instant started = Instant.now();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long durationMs = Duration.between(started, Instant.now()).toMillis();
            return new HttpResult(true, response.statusCode(), response.body(), durationMs, Map.of());
        } catch (IOException e) {
            return new HttpResult(false, 0, "", Duration.between(started, Instant.now()).toMillis(), Map.of("error", e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new HttpResult(false, 0, "", Duration.between(started, Instant.now()).toMillis(), Map.of("error", "interrupted"));
        }
    }

    public record HttpResult(boolean ok, int statusCode, String body, long durationMs, Map<String, Object> details) {
        public Map<String, Object> asMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", ok);
            out.put("statusCode", statusCode);
            out.put("durationMs", durationMs);
            out.put("body", body);
            out.put("details", details);
            return out;
        }
    }
}
