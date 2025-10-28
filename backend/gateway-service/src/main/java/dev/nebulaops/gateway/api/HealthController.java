package dev.nebulaops.gateway.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
            "status",    "UP",
            "service",   "gateway-service",
            "version",   "22.1",
            "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "version", "22.1", "ts", Instant.now().toString());
    }
}
