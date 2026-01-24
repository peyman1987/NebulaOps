package dev.nebulaops.audit.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/audit", "/api/events"})
public class AuditController {
    private final AuditEventStore store;

    public AuditController() {
        this(new AuditEventStore());
    }

    @Autowired
    public AuditController(AuditEventStore store) {
        this.store = store;
    }

    @GetMapping({"/events", ""})
    public ResponseEntity<Object> list(@RequestParam(defaultValue = "100") int limit,
                                       @RequestParam(required = false) String correlationId,
                                       @RequestParam(required = false) String actor,
                                       @RequestParam(required = false) String type) {
        List<Map<String, Object>> filtered = store.list(limit, correlationId, actor, type);
        return ResponseEntity.ok(Map.of(
                "live", true,
                "source", "audit-service",
                "realDataOnly", true,
                "count", filtered.size(),
                "items", filtered,
                "toolStatus", filtered.isEmpty()
                        ? "No runtime audit events recorded yet. Mutative actions will populate this timeline."
                        : "Audit timeline populated from runtime events"
        ));
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<Object> get(@PathVariable String id) {
        return ResponseEntity.ok(store.get(id));
    }

    @GetMapping("/correlation/{correlationId}")
    public ResponseEntity<Object> correlation(@PathVariable String correlationId) {
        return list(100, correlationId, null, null);
    }

    @PostMapping({"/events", ""})
    public ResponseEntity<Object> record(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(store.record(body));
    }

    @PostMapping("/export")
    public ResponseEntity<Object> export(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of(
                "format", body == null ? "json" : body.getOrDefault("format", "json"),
                "generatedAt", Instant.now().toString(),
                "items", store.size(),
                "status", "READY",
                "realDataOnly", true,
                "events", store.all()
        ));
    }
}
