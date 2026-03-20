package com.apiforge.service;

import com.apiforge.model.ApiModels.*;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class RequestExecutor {

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private static String withQuery(String url, List<QueryKV> qs, AuthConfig auth) {
        StringJoiner sj = new StringJoiner("&");
        if (qs != null) {
            for (QueryKV q : qs)
                if (q.enabled() && notBlank(q.key()))
                    sj.add(enc(q.key()) + "=" + enc(q.value() == null ? "" : q.value()));
        }
        if (auth != null && "apiKeyQuery".equals(auth.type())) {
            Map<String, String> v = auth.values() == null ? Map.of() : auth.values();
            if (notBlank(v.get("key"))) sj.add(enc(v.get("key")) + "=" + enc(v.getOrDefault("value", "")));
        }
        if (sj.length() == 0) return url;
        return url + (url.contains("?") ? "&" : "?") + sj;
    }

    private static void applyBodyContentType(String bodyType, Map<String, String> headers, String body) {
        if (body == null || body.isBlank()) return;
        boolean has = headers.keySet().stream().anyMatch(k -> k.equalsIgnoreCase("Content-Type"));
        if (has) return;
        switch (bodyType == null ? "" : bodyType) {
            case "json"      -> headers.put("Content-Type", "application/json");
            case "xml"       -> headers.put("Content-Type", "application/xml");
            case "form"      -> headers.put("Content-Type", "application/x-www-form-urlencoded");
            case "text"      -> headers.put("Content-Type", "text/plain; charset=utf-8");
            case "graphql"   -> headers.put("Content-Type", "application/json");
            default          -> {}
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static void applyAuth(AuthConfig a, Map<String, String> h) {
        if (a == null || a.type() == null) return;
        Map<String, String> v = a.values() == null ? Map.of() : a.values();
        switch (a.type()) {
            case "bearer", "jwt" -> { if (notBlank(v.get("token"))) h.put("Authorization", "Bearer " + v.get("token")); }
            case "basic" -> {
                String raw = v.getOrDefault("username", "") + ":" + v.getOrDefault("password", "");
                h.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
            }
            case "apiKeyHeader" -> { if (notBlank(v.get("key"))) h.put(v.get("key"), v.getOrDefault("value", "")); }
            case "oauth2" -> { if (notBlank(v.get("accessToken"))) h.put("Authorization", "Bearer " + v.get("accessToken")); }
            case "digest" -> {
                // Simplified digest: send as Basic for compatibility
                String raw = v.getOrDefault("username", "") + ":" + v.getOrDefault("password", "");
                h.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
            }
            default -> {}
        }
    }

    private static String buildMultipart(List<FormKV> formData, String boundary) {
        if (formData == null || formData.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (FormKV f : formData) {
            if (!f.enabled() || !notBlank(f.key())) continue;
            sb.append("--").append(boundary).append("\r\n");
            sb.append("Content-Disposition: form-data; name=\"").append(f.key()).append("\"").append("\r\n\r\n");
            sb.append(f.value() == null ? "" : f.value()).append("\r\n");
        }
        sb.append("--").append(boundary).append("--\r\n");
        return sb.toString();
    }

    private static String reason(int c) {
        return switch (c) {
            case 100 -> "Continue"; case 101 -> "Switching Protocols"; case 102 -> "Processing";
            case 200 -> "OK"; case 201 -> "Created"; case 202 -> "Accepted";
            case 204 -> "No Content"; case 206 -> "Partial Content";
            case 301 -> "Moved Permanently"; case 302 -> "Found"; case 304 -> "Not Modified";
            case 307 -> "Temporary Redirect"; case 308 -> "Permanent Redirect";
            case 400 -> "Bad Request"; case 401 -> "Unauthorized"; case 403 -> "Forbidden";
            case 404 -> "Not Found"; case 405 -> "Method Not Allowed"; case 408 -> "Request Timeout";
            case 409 -> "Conflict"; case 410 -> "Gone"; case 413 -> "Payload Too Large";
            case 415 -> "Unsupported Media Type"; case 422 -> "Unprocessable Entity";
            case 429 -> "Too Many Requests"; case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway"; case 503 -> "Service Unavailable"; case 504 -> "Gateway Timeout";
            default -> "HTTP " + c;
        };
    }

    public HttpResponseDto execute(HttpRequestDto r) throws Exception {
        int timeoutSec = (r.timeoutSeconds() != null && r.timeoutSeconds() > 0) ? r.timeoutSeconds() : 60;
        long start = System.nanoTime();
        String url = withQuery(r.url(), r.queryParams(), r.auth());
        URI uri = URI.create(url);
        if (uri.getScheme() == null || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Invalid URL: use http:// or https://");
        }

        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(timeoutSec));
        Map<String, String> headers = new LinkedHashMap<>();
        if (r.headers() != null) {
            for (HeaderKV h : r.headers())
                if (h.enabled() && notBlank(h.key())) headers.put(h.key(), h.value() == null ? "" : h.value());
        }

        String method = r.method() == null ? "GET" : r.method().toUpperCase(Locale.ROOT);
        String body = r.body() == null ? "" : r.body();
        String bodyType = r.bodyType() == null ? "none" : r.bodyType();

        if (method.equals("GRAPHQL")) {
            method = "POST";
            headers.putIfAbsent("Content-Type", "application/json");
        } else if ("multipart".equals(bodyType)) {
            String boundary = "----FormBoundary" + UUID.randomUUID().toString().replace("-", "");
            body = buildMultipart(r.formData(), boundary);
            headers.put("Content-Type", "multipart/form-data; boundary=" + boundary);
        } else {
            applyBodyContentType(bodyType, headers, body);
        }
        applyAuth(r.auth(), headers);
        headers.forEach(b::header);

        if (List.of("GET", "DELETE", "HEAD", "OPTIONS").contains(method) && body.isBlank()) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }

        HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long ms = (System.nanoTime() - start) / 1_000_000;
        String rb = res.body() == null ? "" : res.body();
        List<String> cookies = res.headers().allValues("set-cookie");
        return new HttpResponseDto(
            res.statusCode(), reason(res.statusCode()),
            res.headers().map(), rb, ms,
            rb.getBytes(StandardCharsets.UTF_8).length,
            cookies, Instant.now()
        );
    }
}
