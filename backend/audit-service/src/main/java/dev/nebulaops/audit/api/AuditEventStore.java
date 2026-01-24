package dev.nebulaops.audit.api;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AuditEventStore {
    private final CopyOnWriteArrayList<Map<String, Object>> events = new CopyOnWriteArrayList<>();

    public List<Map<String, Object>> list(int limit, String correlationId, String actor, String type) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        return events.stream()
                .filter(e -> correlationId == null || correlationId.equals(e.get("correlationId")))
                .filter(e -> actor == null || actor.equals(e.get("actor")))
                .filter(e -> type == null || type.equals(e.get("type")))
                .limit(safeLimit)
                .toList();
    }

    public Map<String, Object> get(String id) {
        return events.stream().filter(e -> id.equals(e.get("id"))).findFirst()
                .orElse(Map.of("id", id, "status", "NOT_FOUND", "live", true));
    }

    public Map<String, Object> record(Map<String, Object> body) {
        Map<String, Object> event = new LinkedHashMap<>(body == null ? Map.of() : body);
        event.putIfAbsent("id", "evt-" + UUID.randomUUID());
        event.putIfAbsent("timestamp", Instant.now().toString());
        event.putIfAbsent("severity", "INFO");
        event.putIfAbsent("source", "unknown");
        event.putIfAbsent("actor", "system");
        event.putIfAbsent("correlationId", "corr-" + UUID.randomUUID());
        event.putIfAbsent("payload", Map.of());
        events.add(0, event);
        return event;
    }

    public int size() {
        return events.size();
    }

    public List<Map<String, Object>> all() {
        return events;
    }
}
