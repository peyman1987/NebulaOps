package dev.nebulaops.gateway.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class HttpApiClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> get(String url) {
        long started = System.nanoTime();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("url", safe(url));
        out.put("executedAt", Instant.now().toString());
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            long ms = (System.nanoTime() - started) / 1_000_000;
            Object body;
            try {
                String raw = res.body() == null ? "" : res.body();
                body = raw.isBlank() ? Map.of() : mapper.readValue(raw, Object.class);
            } catch (Exception e) {
                body = res.body() == null ? "" : res.body();
            }
            out.put("live", res.statusCode() >= 200 && res.statusCode() < 500);
            out.put("statusCode", res.statusCode());
            out.put("durationMs", ms);
            out.put("body", body == null ? Map.of() : body);
            return out;
        } catch (Exception e) {
            long ms = (System.nanoTime() - started) / 1_000_000;
            out.put("live", false);
            out.put("statusCode", 0);
            out.put("durationMs", ms);
            out.put("error", safe(e.getMessage()));
            out.put("exception", e.getClass().getSimpleName());
            return out;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
