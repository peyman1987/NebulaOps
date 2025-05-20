package dev.nebulaops.gateway.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.Map;

/**
 * v21.2 — Health check endpoint.
 * Returns version 21.2 metadata so you can confirm the correct image is running.
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
            "status",    "UP",
            "service",   "gateway-service",
            "version",   "21.2",
            "timestamp", Instant.now().toString()
        );
    }

    /** Alias for Spring Boot actuator-style path without management port */
    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "version", "21.2", "ts", Instant.now().toString());
    }
}
