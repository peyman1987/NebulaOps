package dev.nebulaops.gateway.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HealthController {
    public static final String VERSION = "24.1";

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "gateway-service",
            "version", VERSION,
            "release", "NebulaOps v24.1",
            "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of("ok", true, "version", VERSION, "release", "NebulaOps v24.1", "ts", Instant.now().toString());
    }
}
