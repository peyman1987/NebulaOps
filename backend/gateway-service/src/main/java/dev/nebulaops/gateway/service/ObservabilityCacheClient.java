package dev.nebulaops.gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@SuppressWarnings("unchecked")
public class ObservabilityCacheClient {
    private final RestTemplate rest;
    private final String cacheUrl;
    private final int ttlSeconds;

    public ObservabilityCacheClient(RestTemplate rest,
                                    @Value("${nebulaops.cache.url:${GO_CACHE_SERVICE_URL:http://go-cache-service:8091}}") String cacheUrl,
                                    @Value("${nebulaops.cache.observability-ttl-seconds:30}") int ttlSeconds) {
        this.rest = rest;
        this.cacheUrl = cacheUrl == null ? "" : cacheUrl.replaceAll("/+$", "");
        this.ttlSeconds = ttlSeconds;
    }

    public Map<String, Object> get(String source, String query) {
        try {
            Object body = rest.getForObject(cacheUrl + "/cache/observability/" + source + "?query=" + enc(query), Object.class);
            if (body instanceof Map<?, ?> map) return (Map<String, Object>) map;
        } catch (Exception ignored) {}
        return Map.of("cache", "MISS");
    }

    public void put(String source, String query, Object value) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("source", source);
            payload.put("query", query);
            payload.put("value", value == null ? "{}" : String.valueOf(value));
            payload.put("ttlSeconds", ttlSeconds);
            rest.postForObject(cacheUrl + "/cache/observability/" + source + "?query=" + enc(query), payload, Object.class);
        } catch (Exception ignored) {}
    }

    private String enc(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }
}
