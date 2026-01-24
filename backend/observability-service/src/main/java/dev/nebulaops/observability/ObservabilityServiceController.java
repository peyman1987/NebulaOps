package dev.nebulaops.observability;

import dev.nebulaops.observability.service.ObservabilityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityServiceController {
    private final ObservabilityService service;

    public ObservabilityServiceController(ObservabilityService service) {
        this.service = service;
    }

    @GetMapping({"", "/stack"})
    public Map<String, Object> stack() {
        return service.stack();
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(defaultValue = "default-org") String organizationId) {
        return service.overview(organizationId);
    }

    @GetMapping("/services")
    public Map<String, Object> services() {
        return service.services();
    }

    @GetMapping("/metrics/prometheus")
    public Map<String, Object> prometheus(@RequestParam(defaultValue = "up") String query) {
        return service.prometheus(query);
    }

    @GetMapping("/logs/loki")
    public Map<String, Object> loki(@RequestParam(required = false) String query) {
        return service.loki(query);
    }

    @GetMapping("/traces/tempo")
    public Map<String, Object> tempo(@RequestParam(defaultValue = "20") int limit) {
        return service.tempo(limit);
    }

    @GetMapping("/grafana")
    public Map<String, Object> grafana() {
        return service.grafana();
    }

    @GetMapping("/audit/events")
    public Map<String, Object> auditEvents(@RequestParam(defaultValue = "100") int limit) {
        return service.auditEvents(limit);
    }

    @GetMapping("/events/notifications")
    public Map<String, Object> notificationEvents(@RequestParam(defaultValue = "100") int limit) {
        return service.notificationEvents(limit);
    }

    @GetMapping("/events/tasks")
    public Map<String, Object> taskEvents(@RequestParam(defaultValue = "default-org") String organizationId) {
        return service.taskEvents(organizationId);
    }

    @GetMapping("/events/rabbitmq")
    public Map<String, Object> rabbitmq() {
        return service.rabbitmq();
    }
}
