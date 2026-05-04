package dev.nebulaops.gateway.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {
    public static final String VERSION = "23.3";

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "gateway-service",
            "version", VERSION,
            "release", "NebulaOps v23.3",
            "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "version", VERSION, "release", "NebulaOps v23.3", "ts", Instant.now().toString());
    }
}
