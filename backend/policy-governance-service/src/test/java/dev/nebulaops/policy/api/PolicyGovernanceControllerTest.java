package dev.nebulaops.policy.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PolicyGovernanceControllerTest {
    @Test
    void listReturnsPolicies() {
        PolicyGovernanceController controller = new PolicyGovernanceController("http://gateway-service:8080", "http://devsecops-service:8086", "http://cost-analytics-service:8097", "http://audit-service:8101", "http://notification-service:8083");
        ResponseEntity<Object> response = controller.list();
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
    }

    @Test
    void evaluateReturnsPolicyStatus() {
        PolicyGovernanceController controller = new PolicyGovernanceController("http://gateway-service:8080", "http://devsecops-service:8086", "http://cost-analytics-service:8097", "http://audit-service:8101", "http://notification-service:8083");
        ResponseEntity<Object> response = controller.evaluate(Map.of("target", "nebulaops-platform"));
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(String.valueOf(response.getBody()).contains("status"));
    }
}
