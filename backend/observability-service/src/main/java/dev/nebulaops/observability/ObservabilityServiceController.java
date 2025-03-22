package dev.nebulaops.observability;

import dev.nebulaops.observability.service.ObservabilityService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityServiceController {
    private final ObservabilityService service;

    public ObservabilityServiceController(ObservabilityService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> stack() {
        return service.stack();
    }

    @GetMapping("/prometheus")
    public Map<String, Object> prometheus(@RequestParam(defaultValue = "up") String query) {
        return service.prometheus(query);
    }

    @GetMapping("/loki")
    public Map<String, Object> loki(@RequestParam(required = false) String query) {
        return service.loki(query);
    }

    @GetMapping("/grafana")
    public Map<String, Object> grafana() {
        return service.grafana();
    }
}
