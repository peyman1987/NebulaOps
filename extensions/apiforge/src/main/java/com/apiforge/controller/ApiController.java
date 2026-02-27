package com.apiforge.controller;

import com.apiforge.model.ApiModels.CollectionDto;
import com.apiforge.model.ApiModels.EnvironmentDto;
import com.apiforge.model.ApiModels.HistoryDto;
import com.apiforge.model.ApiModels.HttpRequestDto;
import com.apiforge.service.RequestExecutor;
import com.apiforge.service.StorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final StorageService storage;
    private final RequestExecutor executor;

    public ApiController(StorageService s, RequestExecutor e) {
        storage = s;
        executor = e;
    }

    @GetMapping("/live")
    Map<String, Object> live() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<CollectionDto> collectionList = storage.collections();
        List<EnvironmentDto> environmentList = storage.envs();
        List<HistoryDto> historyList = storage.history();
        out.put("status", "CONNECTED");
        out.put("extension", "apiforge");
        out.put("runtime", "Spring Boot MVC");
        out.put("dataPolicy", "LIVE_ONLY");
        out.put("collections", collectionList.size());
        out.put("environments", environmentList.size());
        out.put("history", historyList.size());
        out.put("items", collectionList.stream().map(c -> Map.of("name", c.name(), "requests", c.requests() == null ? 0 : c.requests().size())).toList());
        out.put("generatedAt", Instant.now().toString());
        return out;
    }

    @GetMapping("/capabilities")
    Map<String, Object> capabilities() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "CONNECTED");
        out.put("extension", "apiforge");
        out.put("runtime", "Spring Boot MVC");
        out.put("dataPolicy", "LIVE_ONLY");
        out.put("endpoints", List.of(
                Map.of("method", "GET", "path", "/api/live", "purpose", "Workspace status from persistent storage"),
                Map.of("method", "GET", "path", "/api/collections", "purpose", "Stored request collections"),
                Map.of("method", "POST", "path", "/api/send", "purpose", "Execute a real HTTP/GraphQL request"),
                Map.of("method", "GET", "path", "/api/history", "purpose", "Executed request history")
        ));
        out.put("generatedAt", Instant.now().toString());
        return out;
    }

    @GetMapping("/collections")
    List<CollectionDto> collections() {
        return storage.collections();
    }

    @PostMapping("/collections")
    CollectionDto saveCollection(@RequestBody CollectionDto c) {
        return storage.saveCollection(c);
    }

    @DeleteMapping("/collections/{id}")
    void delete(@PathVariable String id) {
        storage.deleteCollection(id);
    }

    @GetMapping("/environments")
    List<EnvironmentDto> envs() {
        return storage.envs();
    }

    @GetMapping("/history")
    List<HistoryDto> history() {
        return storage.history();
    }

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

    @GetMapping("/export/{id}")
    ResponseEntity<CollectionDto> export(@PathVariable String id) {
        var c = storage.collections().stream().filter(x -> x.id().equals(id)).findFirst();
        if (c.isEmpty()) return ResponseEntity.notFound().build();
        var fileName = c.get().name().replaceAll("\\W+", "_") + ".postman_collection.json";
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=" + fileName).body(c.get());
    }
}
