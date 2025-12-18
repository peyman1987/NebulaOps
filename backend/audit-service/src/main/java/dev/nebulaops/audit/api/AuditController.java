package dev.nebulaops.audit.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping({"/api/audit", "/api/events"})
public class AuditController {
    private final CopyOnWriteArrayList<Map<String,Object>> events = new CopyOnWriteArrayList<>();

    @GetMapping({"/events", ""})
    public ResponseEntity<Object> list(@RequestParam(defaultValue="100") int limit,
                                       @RequestParam(required=false) String correlationId,
                                       @RequestParam(required=false) String actor,
                                       @RequestParam(required=false) String type) {
        List<Map<String,Object>> filtered = events.stream()
            .filter(e -> correlationId == null || correlationId.equals(e.get("correlationId")))
            .filter(e -> actor == null || actor.equals(e.get("actor")))
            .filter(e -> type == null || type.equals(e.get("type")))
            .limit(limit)
            .toList();

        return ResponseEntity.ok(Map.of(
            "live", true,
            "source", "audit-service",
            "count", filtered.size(),
            "items", filtered,
            "toolStatus", filtered.isEmpty() ? "No audit events recorded yet. Mutative actions will populate this timeline." : "Audit timeline populated from runtime events"
        ));
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<Object> get(@PathVariable String id) {
        return ResponseEntity.ok(events.stream().filter(e -> id.equals(e.get("id"))).findFirst()
            .orElse(Map.of("id", id, "status","NOT_FOUND", "live", true)));
    }

    @GetMapping("/correlation/{correlationId}")
    public ResponseEntity<Object> correlation(@PathVariable String correlationId) {
        return list(100, correlationId, null, null);
    }

    @PostMapping({"/events", ""})
    public ResponseEntity<Object> record(@RequestBody Map<String,Object> body) {
        Map<String,Object> e = new LinkedHashMap<>(body == null ? Map.of() : body);
        e.putIfAbsent("id", "evt-" + UUID.randomUUID());
        e.putIfAbsent("timestamp", Instant.now().toString());
        e.putIfAbsent("severity", "INFO");
        e.putIfAbsent("source", "unknown");
        e.putIfAbsent("actor", "system");
        e.putIfAbsent("correlationId", "corr-" + UUID.randomUUID());
        e.putIfAbsent("payload", Map.of());
        events.add(0, e);
        return ResponseEntity.ok(e);
    }

    @PostMapping("/export")
    public ResponseEntity<Object> export(@RequestBody(required=false) Map<String,Object> body) {
        return ResponseEntity.ok(Map.of(
            "format", body == null ? "json" : body.getOrDefault("format","json"),
            "generatedAt", Instant.now().toString(),
            "items", events.size(),
            "status", "READY",
            "events", events
        ));
    }
}
