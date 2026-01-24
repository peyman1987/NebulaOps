package dev.nebulaops.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AuditClient {
    private final RestTemplate rest = new RestTemplate();
    private final String auditUrl;

    public AuditClient(@Value("${nebulaops.audit.url:http://audit-service:8101}") String auditUrl) {
        this.auditUrl = auditUrl.replaceAll("/+$", "");
    }

    public void identityEvent(String realm, String action, String resourceType, String resourceId, Map<String, Object> payload) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "IDENTITY_" + action.toUpperCase());
            event.put("source", "auth-service");
            event.put("actor", "keycloak-admin");
            event.put("correlationId", "identity-" + UUID.randomUUID());
            event.put("timestamp", Instant.now().toString());
            event.put("severity", "INFO");
            event.put("payload", Map.of(
                    "realm", realm,
                    "resourceType", resourceType,
                    "resourceId", resourceId == null ? "" : resourceId,
                    "data", payload == null ? Map.of() : payload
            ));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            rest.postForObject(auditUrl + "/api/audit/events", new HttpEntity<>(event, headers), Object.class);
        } catch (Exception ignored) {
            // Audit must never block the Keycloak mutation path.
        }
    }
}
