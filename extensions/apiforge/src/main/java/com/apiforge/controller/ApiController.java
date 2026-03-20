package com.apiforge.controller;

import com.apiforge.model.ApiModels.*;
import com.apiforge.service.RequestExecutor;
import com.apiforge.service.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final StorageService storage;
    private final RequestExecutor executor;

    public ApiController(StorageService s, RequestExecutor e) { storage = s; executor = e; }

    // ── Health / Capabilities ─────────────────────────────────────────────────

    @GetMapping("/live")
    Map<String, Object> live() {
        var cols = storage.collections();
        var envs = storage.envs();
        var hist = storage.history();
        return Map.of(
            "status", "CONNECTED", "extension", "apiforge",
            "runtime", "Spring Boot MVC", "dataPolicy", "LIVE_ONLY",
            "collections", cols.size(), "environments", envs.size(), "history", hist.size(),
            "items", cols.stream().map(c -> Map.of("name", c.name(), "requests", c.requests() == null ? 0 : c.requests().size())).toList(),
            "generatedAt", Instant.now().toString()
        );
    }

    @GetMapping("/capabilities")
    Map<String, Object> capabilities() {
        return Map.of(
            "status", "CONNECTED", "extension", "apiforge",
            "runtime", "Spring Boot MVC", "dataPolicy", "LIVE_ONLY",
            "endpoints", List.of(
                Map.of("method","GET","path","/api/collections","purpose","Request collections"),
                Map.of("method","POST","path","/api/send","purpose","Execute HTTP/GraphQL request"),
                Map.of("method","POST","path","/api/runner","purpose","Run entire collection"),
                Map.of("method","POST","path","/api/codegen","purpose","Generate code snippets"),
                Map.of("method","GET","path","/api/history","purpose","Request history"),
                Map.of("method","GET","path","/api/environments","purpose","Environments"),
                Map.of("method","GET","path","/api/export/{id}","purpose","Export collection as Postman JSON")
            ),
            "generatedAt", Instant.now().toString()
        );
    }

    // ── Collections ───────────────────────────────────────────────────────────

    @GetMapping("/collections")
    List<CollectionDto> collections() { return storage.collections(); }

    @PostMapping("/collections")
    CollectionDto saveCollection(@RequestBody CollectionDto c) { return storage.saveCollection(c); }

    @DeleteMapping("/collections/{id}")
    void deleteCollection(@PathVariable String id) { storage.deleteCollection(id); }

    // ── Environments ──────────────────────────────────────────────────────────

    @GetMapping("/environments")
    List<EnvironmentDto> envs() { return storage.envs(); }

    @PostMapping("/environments")
    EnvironmentDto saveEnvironment(@RequestBody EnvironmentDto e) { return storage.saveEnvironment(e); }

    @DeleteMapping("/environments/{id}")
    void deleteEnvironment(@PathVariable String id) { storage.deleteEnvironment(id); }

    // ── History ───────────────────────────────────────────────────────────────

    @GetMapping("/history")
    List<HistoryDto> history() { return storage.history(); }

    @DeleteMapping("/history")
    void clearHistory() { storage.clearHistory(); }

    // ── Send ──────────────────────────────────────────────────────────────────

    @PostMapping("/send")
    ResponseEntity<?> send(@RequestBody HttpRequestDto req) {
        try {
            var res = executor.execute(req);
            storage.addHistory(req, res);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Collection Runner ─────────────────────────────────────────────────────

    @PostMapping("/runner")
    ResponseEntity<?> runCollection(@RequestBody RunnerRequestDto runReq) {
        var col = storage.collections().stream()
            .filter(c -> c.id().equals(runReq.collectionId())).findFirst();
        if (col.isEmpty()) return ResponseEntity.notFound().build();

        List<Map<String, Object>> results = new ArrayList<>();
        int passed = 0, failed = 0;
        long totalMs = 0;

        for (var stored : col.get().requests()) {
            var req = stored.request();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", stored.id());
            result.put("name", stored.name());
            result.put("method", req.method());
            result.put("url", req.url());

            try {
                if (runReq.delayMs() > 0) Thread.sleep(Math.min(runReq.delayMs(), 5000));
                var res = executor.execute(req);
                storage.addHistory(req, res);
                result.put("status", res.status());
                result.put("timeMs", res.timeMs());
                result.put("sizeBytes", res.sizeBytes());
                result.put("passed", res.status() >= 200 && res.status() < 300);
                result.put("error", null);
                totalMs += res.timeMs();
                if (res.status() >= 200 && res.status() < 300) passed++; else failed++;

                if (runReq.stopOnError() && (res.status() >= 400)) {
                    result.put("stopped", true);
                    results.add(result);
                    break;
                }
            } catch (Exception e) {
                result.put("status", 0);
                result.put("timeMs", 0);
                result.put("passed", false);
                result.put("error", e.getMessage());
                failed++;
                if (runReq.stopOnError()) { results.add(result); break; }
            }
            results.add(result);
        }

        return ResponseEntity.ok(Map.of(
            "collectionId", runReq.collectionId(),
            "collectionName", col.get().name(),
            "total", results.size(),
            "passed", passed, "failed", failed,
            "totalMs", totalMs,
            "results", results,
            "timestamp", Instant.now().toString()
        ));
    }

    // ── Code Generation ───────────────────────────────────────────────────────

    @PostMapping("/codegen")
    ResponseEntity<?> codegen(@RequestBody CodegenRequest req) {
        String snippet = switch (req.language()) {
            case "javascript" -> buildJsSnippet(req.request());
            case "python"     -> buildPythonSnippet(req.request());
            case "php"        -> buildPhpSnippet(req.request());
            case "go"         -> buildGoSnippet(req.request());
            case "java"       -> buildJavaSnippet(req.request());
            case "curl"       -> buildCurlSnippet(req.request());
            default           -> "Unsupported language: " + req.language();
        };
        return ResponseEntity.ok(Map.of("language", req.language(), "snippet", snippet));
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @GetMapping("/export/{id}")
    ResponseEntity<CollectionDto> export(@PathVariable String id) {
        var c = storage.collections().stream().filter(x -> x.id().equals(id)).findFirst();
        if (c.isEmpty()) return ResponseEntity.notFound().build();
        var fileName = c.get().name().replaceAll("\\W+", "_") + ".postman_collection.json";
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=" + fileName)
            .body(c.get());
    }

    // ── Code generators ───────────────────────────────────────────────────────

    private String buildCurlSnippet(HttpRequestDto r) {
        var sb = new StringBuilder("curl");
        String m = r.method() != null ? r.method() : "GET";
        if (!"GET".equals(m)) sb.append(" -X ").append(m.equals("GRAPHQL") ? "POST" : m);
        sb.append(" \\\n  '").append(r.url()).append("'");
        if (r.headers() != null)
            for (var h : r.headers())
                if (h.enabled() && h.key() != null && !h.key().isBlank())
                    sb.append(" \\\n  -H '").append(h.key()).append(": ").append(h.value()).append("'");
        applyAuthCurl(r, sb);
        if (r.body() != null && !r.body().isBlank())
            sb.append(" \\\n  --data-raw '").append(r.body().replace("'","'\\''")).append("'");
        return sb.toString();
    }

    private String buildJsSnippet(HttpRequestDto r) {
        var headers = buildHeadersObj(r);
        String method = r.method() != null ? r.method().equals("GRAPHQL") ? "POST" : r.method() : "GET";
        String body = (r.body() != null && !r.body().isBlank()) ? "  body: JSON.stringify(" + r.body() + ")," : "";
        return "const response = await fetch('" + r.url() + "', {\n" +
            "  method: '" + method + "',\n" +
            "  headers: " + headers + ",\n" +
            (body.isEmpty() ? "" : body + "\n") +
            "});\n\n" +
            "const data = await response.json();\n" +
            "console.log(response.status, data);";
    }

    private String buildPythonSnippet(HttpRequestDto r) {
        var sb = new StringBuilder();
        sb.append("import requests\n\n");
        sb.append("headers = ").append(buildPythonDict(headersMap(r))).append("\n\n");
        String method = r.method() != null ? r.method().equals("GRAPHQL") ? "POST" : r.method() : "GET";
        String body = (r.body() != null && !r.body().isBlank()) ? "data='" + r.body().replace("'", "\\'") + "', " : "";
        sb.append("response = requests.").append(method.toLowerCase()).append("(\n");
        sb.append("    '").append(r.url()).append("',\n");
        sb.append("    headers=headers,\n");
        if (!body.isEmpty()) sb.append("    data='").append(r.body().replace("'", "\\'")).append("',\n");
        sb.append(")\n\n");
        sb.append("print(response.status_code)\n");
        sb.append("print(response.json())");
        return sb.toString();
    }

    private String buildPhpSnippet(HttpRequestDto r) {
        String method = r.method() != null ? r.method().equals("GRAPHQL") ? "POST" : r.method() : "GET";
        var sb = new StringBuilder();
        sb.append("<?php\n$client = new GuzzleHttp\\Client();\n\n");
        sb.append("$response = $client->request('").append(method).append("', '").append(r.url()).append("', [\n");
        sb.append("    'headers' => [\n");
        if (r.headers() != null)
            for (var h : r.headers())
                if (h.enabled() && h.key() != null && !h.key().isBlank())
                    sb.append("        '").append(h.key()).append("' => '").append(h.value()).append("',\n");
        sb.append("    ],\n");
        if (r.body() != null && !r.body().isBlank())
            sb.append("    'body' => '").append(r.body().replace("'", "\\'")).append("',\n");
        sb.append("]);\n\necho $response->getStatusCode();\necho $response->getBody();");
        return sb.toString();
    }

    private String buildGoSnippet(HttpRequestDto r) {
        String method = r.method() != null ? r.method().equals("GRAPHQL") ? "POST" : r.method() : "GET";
        boolean hasBody = r.body() != null && !r.body().isBlank();
        var sb = new StringBuilder();
        sb.append("package main\n\nimport (\n    \"fmt\"\n    \"io\"\n    \"net/http\"\n");
        if (hasBody) sb.append("    \"strings\"\n");
        sb.append(")\n\nfunc main() {\n");
        if (hasBody) {
            sb.append("    body := strings.NewReader(`").append(r.body()).append("`)\n");
            sb.append("    req, _ := http.NewRequest(\"").append(method).append("\", \"").append(r.url()).append("\", body)\n");
        } else {
            sb.append("    req, _ := http.NewRequest(\"").append(method).append("\", \"").append(r.url()).append("\", nil)\n");
        }
        if (r.headers() != null)
            for (var h : r.headers())
                if (h.enabled() && h.key() != null && !h.key().isBlank())
                    sb.append("    req.Header.Set(\"").append(h.key()).append("\", \"").append(h.value()).append("\")\n");
        sb.append("\n    client := &http.Client{}\n    resp, err := client.Do(req)\n");
        sb.append("    if err != nil { panic(err) }\n    defer resp.Body.Close()\n");
        sb.append("    respBody, _ := io.ReadAll(resp.Body)\n");
        sb.append("    fmt.Println(resp.Status)\n    fmt.Println(string(respBody))\n}");
        return sb.toString();
    }

    private String buildJavaSnippet(HttpRequestDto r) {
        String method = r.method() != null ? r.method().equals("GRAPHQL") ? "POST" : r.method() : "GET";
        boolean hasBody = r.body() != null && !r.body().isBlank();
        var sb = new StringBuilder();
        sb.append("import java.net.http.*;\nimport java.net.URI;\n\nvar client = HttpClient.newHttpClient();\n\n");
        if (hasBody)
            sb.append("var body = HttpRequest.BodyPublishers.ofString(\"\"\"\n    ").append(r.body()).append("\n    \"\"\");\n\n");
        sb.append("var request = HttpRequest.newBuilder()\n");
        sb.append("    .uri(URI.create(\"").append(r.url()).append("\"))\n");
        if (r.headers() != null)
            for (var h : r.headers())
                if (h.enabled() && h.key() != null && !h.key().isBlank())
                    sb.append("    .header(\"").append(h.key()).append("\", \"").append(h.value()).append("\")\n");
        if (hasBody)
            sb.append("    .method(\"").append(method).append("\", body)\n");
        else
            sb.append("    .").append(method.equals("GET") ? "GET()" : "method(\"" + method + "\", HttpRequest.BodyPublishers.noBody())").append("\n");
        sb.append("    .build();\n\n");
        sb.append("var response = client.send(request, HttpResponse.BodyHandlers.ofString());\n");
        sb.append("System.out.println(response.statusCode());\nSystem.out.println(response.body());");
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyAuthCurl(HttpRequestDto r, StringBuilder sb) {
        if (r.auth() == null) return;
        var v = r.auth().values() == null ? Map.<String,String>of() : r.auth().values();
        switch (r.auth().type() == null ? "" : r.auth().type()) {
            case "bearer","jwt" -> { if (v.get("token") != null) sb.append(" \\\n  -H 'Authorization: Bearer ").append(v.get("token")).append("'"); }
            case "basic" -> sb.append(" \\\n  -u '").append(v.getOrDefault("username","")).append(":").append(v.getOrDefault("password","")).append("'");
            case "oauth2" -> { if (v.get("accessToken") != null) sb.append(" \\\n  -H 'Authorization: Bearer ").append(v.get("accessToken")).append("'"); }
        }
    }

    private Map<String, String> headersMap(HttpRequestDto r) {
        Map<String, String> m = new LinkedHashMap<>();
        if (r.headers() != null)
            for (var h : r.headers())
                if (h.enabled() && h.key() != null && !h.key().isBlank()) m.put(h.key(), h.value());
        return m;
    }

    private String buildHeadersObj(HttpRequestDto r) {
        var m = headersMap(r);
        if (m.isEmpty()) return "{}";
        var sb = new StringBuilder("{\n");
        m.forEach((k,v) -> sb.append("    '").append(k).append("': '").append(v).append("',\n"));
        sb.append("  }");
        return sb.toString();
    }

    private String buildPythonDict(Map<String, String> m) {
        if (m.isEmpty()) return "{}";
        var sb = new StringBuilder("{\n");
        m.forEach((k,v) -> sb.append("    '").append(k).append("': '").append(v).append("',\n"));
        sb.append("}");
        return sb.toString();
    }
}
