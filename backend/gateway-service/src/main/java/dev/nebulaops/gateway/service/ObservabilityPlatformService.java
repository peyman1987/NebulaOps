package dev.nebulaops.gateway.service;

import dev.nebulaops.gateway.client.HttpApiClient;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ObservabilityPlatformService {
    private final HttpApiClient http;
    private final ObservabilityCacheClient cache;

    public ObservabilityPlatformService(HttpApiClient http, ObservabilityCacheClient cache) {
        this.http = http;
        this.cache = cache;
    }

    public Map<String, Object> stack() {
        List<Map<String, Object>> stack = new ArrayList<>();
        stack.add(endpoint("Prometheus", "metrics", env("PROMETHEUS_URL", "http://prometheus:9090"), "/-/ready"));
        stack.add(endpoint("Loki", "logs", env("LOKI_URL", "http://loki:3100"), "/ready"));
        stack.add(endpoint("Tempo", "traces", env("TEMPO_URL", "http://tempo:3200"), "/ready"));
        stack.add(endpoint("Grafana", "dashboards", env("GRAFANA_URL", "http://grafana:3000"), "/api/health"));
        stack.add(endpoint("OpenTelemetry", "collector", env("OTEL_COLLECTOR_URL", "http://otel-collector:4318"), "/"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", stack.stream().anyMatch(s -> Boolean.TRUE.equals(s.get("live"))));
        out.put("generatedAt", Instant.now().toString());
        out.put("mode", "LIVE_ONLY");
        out.put("stack", stack);
        out.put("items", stack);
        return out;
    }

    public Map<String, Object> prometheusQuery(String query) {
        String raw = query == null || query.isBlank() ? "up" : query;
        return cachedQuery("prometheus", raw, env("PROMETHEUS_URL", "http://prometheus:9090") + "/api/v1/query?query=" + enc(raw));
    }

    public Map<String, Object> lokiQuery(String query) {
        String raw = query == null || query.isBlank() ? "{job=~\".+\"}" : query;
        return cachedQuery("loki", raw, env("LOKI_URL", "http://loki:3100") + "/loki/api/v1/query?query=" + enc(raw));
    }

    public Map<String, Object> grafanaHealth() {
        return http.get(env("GRAFANA_URL", "http://grafana:3000") + "/api/health");
    }

    private Map<String, Object> cachedQuery(String source, String rawQuery, String url) {
        Map<String, Object> cached = cache.get(source, rawQuery);
        if ("HIT".equals(String.valueOf(cached.get("cache")))) {
            Map<String, Object> out = new LinkedHashMap<>(cached);
            out.put("live", true);
            out.put("source", source);
            return out;
        }
        Map<String, Object> live = http.get(url);
        if (Boolean.TRUE.equals(live.get("live"))) cache.put(source, rawQuery, live);
        Map<String, Object> out = new LinkedHashMap<>(live);
        out.put("cache", "MISS");
        out.put("source", source);
        return out;
    }

    private Map<String, Object> endpoint(String name, String role, String base, String path) {
        Map<String, Object> probe = http.get(base + path);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", name);
        out.put("role", role);
        out.put("endpoint", base);
        out.put("live", Boolean.TRUE.equals(probe.get("live")));
        out.put("health", Boolean.TRUE.equals(probe.get("live")) ? 100 : 0);
        out.put("signal", Boolean.TRUE.equals(probe.get("live")) ? "HTTP " + probe.get("statusCode") + " in " + probe.get("durationMs") + "ms" : String.valueOf(probe.getOrDefault("error", "unreachable")));
        out.put("probe", probe);
        return out;
    }

    private String enc(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
    private String env(String key, String fallback) { return System.getenv().getOrDefault(key, fallback); }
}
