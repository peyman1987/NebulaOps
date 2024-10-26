package dev.nebulaops.observability;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityServiceController {
    @GetMapping("/stack")
    public Map<String, Object> stack() {
        return Map.of("version", "19.5", "stack", List.of("Prometheus", "Loki", "Tempo", "Grafana", "OpenTelemetry"), "generatedAt", Instant.now().toString());
    }

    @GetMapping("/traces")
    public List<Map<String, Object>> traces() {
        return List.of(Map.of("service", "gateway", "latencyMs", 24, "status", "ok"), Map.of("service", "task-service", "latencyMs", 112, "status", "hot"));
    }
}