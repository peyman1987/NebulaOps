package dev.nebulaops.gateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class PlatformEventPublisher {
    private final RestTemplate rest;
    private final String auditUrl;
    private final String notificationUrl;

    public PlatformEventPublisher(RestTemplate rest,
                                  @Value("${proxy.audit:http://audit-service:8101}") String auditUrl,
                                  @Value("${proxy.notification:http://notification-service:8083}") String notificationUrl) {
        this.rest = rest;
        this.auditUrl = auditUrl;
        this.notificationUrl = notificationUrl;
    }

    public String publish(String type, String source, String actor, String severity, String correlationId, Map<String, Object> payload) {
        String cid = correlationId == null || correlationId.isBlank() ? "corr-" + UUID.randomUUID() : correlationId;
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "evt-" + UUID.randomUUID());
        event.put("type", type);
        event.put("source", source);
        event.put("actor", actor == null || actor.isBlank() ? "system" : actor);
        event.put("severity", severity == null || severity.isBlank() ? "INFO" : severity);
        event.put("correlationId", cid);
        event.put("timestamp", Instant.now().toString());
        event.put("payload", payload == null ? Map.of() : payload);

        try {
            rest.postForObject(auditUrl + "/api/events", event, Object.class);
        } catch (Exception ignored) {
            // Audit publishing must never break the operator action.
        }

        try {
            Map<String, Object> notification = new LinkedHashMap<>();
            notification.put("type", type);
            notification.put("source", source);
            notification.put("severity", event.get("severity"));
            notification.put("message", type + " from " + source);
            notification.put("correlationId", cid);
            notification.put("payload", payload == null ? Map.of() : payload);
            rest.postForObject(notificationUrl + "/api/notifications", notification, Object.class);
        } catch (Exception ignored) {
            // Notification publishing is best-effort.
        }

        return cid;
    }

    public String mutation(String action, String target, boolean ok, Map<String, Object> payload) {
        return publish(action, "gateway-service", "operator", ok ? "INFO" : "WARN", null,
            Map.of("target", target, "ok", ok, "details", payload == null ? Map.of() : payload));
    }
}
