package dev.nebulaops.audit.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuditControllerTest {
    @Test
    void listReturnsEvents() {
        AuditController controller = new AuditController();
        ResponseEntity<Object> response = controller.list(10, null, null);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
    }

    @Test
    void recordAddsEvent() {
        AuditController controller = new AuditController();
        ResponseEntity<Object> response = controller.record(Map.of("type", "UNIT_TEST", "source", "test"));
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertTrue(String.valueOf(response.getBody()).contains("UNIT_TEST"));
    }
}
