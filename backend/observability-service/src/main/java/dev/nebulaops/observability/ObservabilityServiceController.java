package dev.nebulaops.observability;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityServiceController {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(2)).build();
    private final String prometheus = System.getenv().getOrDefault("PROMETHEUS_URL", "http://prometheus:9090");
    private final String loki = System.getenv().getOrDefault("LOKI_URL", "http://loki:3100");
    private final String tempo = System.getenv().getOrDefault("TEMPO_URL", "http://tempo:3200");
    private final String grafana = System.getenv().getOrDefault("GRAFANA_URL", "http://grafana:3000");

    @GetMapping("/stack")
    public Map<String, Object> stack() {
        List<Map<String, Object>> services = List.of(
                probe("Prometheus", prometheus + "/-/ready"),
                probe("Loki", loki + "/ready"),
                probe("Tempo", tempo + "/ready"),
                probe("Grafana", grafana + "/api/health")
        );
        return Map.of("version", "20.5-real-backend", "live", true, "stack", services, "generatedAt", Instant.now().toString());
    }

    @GetMapping("/traces")
    public Map<String, Object> traces() {
        List<Map<String, Object>> latency = prometheusVector("histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, job))");
        List<Map<String, Object>> up = prometheusVector("up");
        return Map.of(
                "live", true,
                "source", prometheus,
                "latencyP95", latency,
                "targets", up,
                "logs", lokiLabels(),
                "generatedAt", Instant.now().toString()
        );
    }

    @GetMapping("/metrics/{query}")
    public Map<String, Object> metrics(@PathVariable String query) {
        return Map.of("query", query, "result", prometheusVector(query), "generatedAt", Instant.now().toString());
    }

    private List<Map<String, Object>> prometheusVector(String query) {
        try {
            String url = prometheus + "/api/v1/query?query=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> root = getJson(url);
            Map<String, Object> data = (Map<String, Object>) root.getOrDefault("data", Map.of());
            List<Map<String, Object>> result = (List<Map<String, Object>>) data.getOrDefault("result", List.of());
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> r : result) {
                Map<String, Object> metric = (Map<String, Object>) r.getOrDefault("metric", Map.of());
                List<?> value = (List<?>) r.getOrDefault("value", List.of(0, "0"));
                rows.add(Map.of("metric", metric, "value", value.size() > 1 ? value.get(1) : "0"));
            }
            return rows;
        } catch (Exception e) {
            return List.of(Map.of("error", e.getMessage(), "source", prometheus));
        }
    }

    private List<String> lokiLabels() {
        try {
            Map<String, Object> root = getJson(loki + "/loki/api/v1/labels");
            Object data = root.getOrDefault("data", List.of());
            if (data instanceof List<?> l) return l.stream().map(Object::toString).toList();
        } catch (Exception ignored) {
        }
        return List.of();
    }

    private Map<String, Object> probe(String name, String url) {
        long start = System.currentTimeMillis();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return Map.of("name", name, "url", url, "status", res.statusCode(), "reachable", res.statusCode() < 500, "latencyMs", System.currentTimeMillis() - start);
        } catch (Exception e) {
            return Map.of("name", name, "url", url, "status", 0, "reachable", false, "latencyMs", System.currentTimeMillis() - start, "error", e.getMessage());
        }
    }

    private Map<String, Object> getJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        return mapper.readValue(res.body(), new TypeReference<>() {
        });
    }
}
