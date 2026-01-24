package dev.nebulaops.notification.api;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final CopyOnWriteArrayList<Map<String, Object>> inbox = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private volatile Map<String, Object> preferences = Map.of(
        "deployment", true,
        "policy", true,
        "incident", true,
        "security", true,
        "cost", true
    );

    @RabbitListener(queues = "nebula.task.events")
    public void onTaskEvent(Map<String, Object> event) {
        String message = String.valueOf(event.getOrDefault("notificationMessage", "Task event received from RabbitMQ"));
        String type = String.valueOf(event.getOrDefault("type", "TASK_EVENT"));
        addNotification(type, message, "INFO", "task-service", event);
    }

    @GetMapping
    public Object list(@RequestParam(defaultValue = "100") int limit) {
        List<Map<String, Object>> items = inbox.stream().limit(limit).toList();
        return Map.of(
            "live", true,
            "source", "notification-service",
            "count", items.size(),
            "items", items,
            "toolStatus", items.isEmpty() ? "No notifications recorded yet. Release, policy, AI Ops, DevSecOps and FinOps events will populate this stream." : "Notifications populated from runtime events"
        );
    }

    @PostMapping
    public Object create(@RequestBody Map<String, Object> body) {
        String type = String.valueOf(body.getOrDefault("type", "PLATFORM_EVENT"));
        String message = String.valueOf(body.getOrDefault("message", "Platform notification"));
        String severity = String.valueOf(body.getOrDefault("severity", "INFO"));
        String source = String.valueOf(body.getOrDefault("source", "manual"));
        return addNotification(type, message, severity, source, body);
    }

    @PatchMapping("/{id}/read")
    public Object markRead(@PathVariable String id) {
        for (int i = 0; i < inbox.size(); i++) {
            Map<String, Object> item = inbox.get(i);
            if (id.equals(item.get("id"))) {
                Map<String, Object> updated = new java.util.LinkedHashMap<>(item);
                updated.put("read", true);
                updated.put("readAt", Instant.now().toString());
                inbox.set(i, updated);
                return updated;
            }
        }
        return Map.of("id", id, "updated", false);
    }

    @PostMapping("/preferences")
    public Object updatePreferences(@RequestBody Map<String, Object> body) {
        preferences = new java.util.LinkedHashMap<>(body);
        return Map.of("saved", true, "preferences", preferences);
    }

    @GetMapping("/preferences")
    public Object preferences() {
        return preferences;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(error -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of("status", "connected", "timestamp", Instant.now().toString(), "source", "notification-service")));
        } catch (IOException ignored) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    private Map<String, Object> addNotification(String type, String message, String severity, String source, Map<String, Object> payload) {
        Map<String, Object> notification = new java.util.LinkedHashMap<>();
        notification.put("id", "ntf-" + UUID.randomUUID());
        notification.put("type", type);
        notification.put("message", message);
        notification.put("severity", severity);
        notification.put("source", source);
        notification.put("payload", payload);
        notification.put("read", false);
        notification.put("createdAt", Instant.now().toString());
        inbox.add(0, notification);
        broadcast(notification);
        return notification;
    }

    private void broadcast(Map<String, Object> notification) {
        for (SseEmitter emitter : List.copyOf(emitters)) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(notification));
            } catch (IOException ex) {
                emitters.remove(emitter);
            }
        }
    }
}
