package dev.nebulaops.observability.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ObservabilityService {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> stack() {
        return Map.of("live", true, "items", List.of(probe("prometheus", env("PROMETHEUS_URL", "http://prometheus:9090"), "/-/ready"), probe("loki", env("LOKI_URL", "http://loki:3100"), "/ready"), probe("tempo", env("TEMPO_URL", "http://tempo:3200"), "/ready"), probe("grafana", env("GRAFANA_URL", "http://grafana:3000"), "/api/health"), probe("otel-collector", env("OTEL_COLLECTOR_URL", "http://otel-collector:4318"), "/")));
    }

    public Map<String, Object> prometheus(String query) {
        return get(env("PROMETHEUS_URL", "http://prometheus:9090") + "/api/v1/query?query=" + URLEncoder.encode(query == null ? "up" : query, StandardCharsets.UTF_8));
    }

    public Map<String, Object> loki(String query) {
        return get(env("LOKI_URL", "http://loki:3100") + "/loki/api/v1/query?query=" + URLEncoder.encode(query == null ? "{job=~\".+\"}" : query, StandardCharsets.UTF_8));
    }

    public Map<String, Object> grafana() {
        return get(env("GRAFANA_URL", "http://grafana:3000") + "/api/health");
    }

    private Map<String, Object> probe(String name, String base, String path) {
        return Map.of("name", name, "endpoint", base, "probe", get(base + path));
    }

    private Map<String, Object> get(String url) {
        long started = System.nanoTime();
        try {
            var req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).GET().build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            Object body;
            try {
                body = mapper.readValue(res.body(), Object.class);
            } catch (Exception e) {
                body = res.body();
            }
            return Map.of("live", res.statusCode() < 500, "url", url, "statusCode", res.statusCode(), "durationMs", (System.nanoTime() - started) / 1_000_000, "body", body, "executedAt", Instant.now().toString());
        } catch (Exception e) {
            return Map.of("live", false, "url", url, "statusCode", 0, "durationMs", (System.nanoTime() - started) / 1_000_000, "error", e.getMessage(), "executedAt", Instant.now().toString());
        }
    }

    private String env(String key, String fallback) {
        return System.getenv().getOrDefault(key, fallback);
    }
}
