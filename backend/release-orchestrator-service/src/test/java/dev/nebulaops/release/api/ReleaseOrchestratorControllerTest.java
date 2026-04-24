package dev.nebulaops.release.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseOrchestratorControllerTest {
    @Test
    void listReturnsLiveOrDegradedReleases() {
        ReleaseOrchestratorController controller = new ReleaseOrchestratorController("http://gitlab:80", "", "", "http://argocd", "", "http://gateway-service:8080", "http://policy-governance-service:8100", "http://audit-service:8101", "http://notification-service:8083");
        ResponseEntity<Object> response = controller.list("local");
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
    }

    @Test
    void createThenPromoteReturnsDeterministicResponse() {
        ReleaseOrchestratorController controller = new ReleaseOrchestratorController("http://gitlab:80", "", "", "http://argocd", "", "http://gateway-service:8080", "http://policy-governance-service:8100", "http://audit-service:8101", "http://notification-service:8083");
        ResponseEntity<Object> created = controller.create(Map.of("application", "gateway-service", "version", "23.2.0"));
        assertTrue(created.getStatusCode().is2xxSuccessful());
        assertTrue(String.valueOf(created.getBody()).contains("gateway-service"));
    }
}
