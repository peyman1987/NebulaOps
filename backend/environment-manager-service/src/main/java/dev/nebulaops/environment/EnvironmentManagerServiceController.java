package dev.nebulaops.environment;

import dev.nebulaops.environment.service.EnvironmentService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/environments")
public class EnvironmentManagerServiceController {
    private final EnvironmentService service;

    public EnvironmentManagerServiceController(EnvironmentService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> namespaces() {
        return service.namespaces();
    }

    @GetMapping("/{namespace}")
    public Map<String, Object> namespace(@PathVariable String namespace) {
        return service.namespace(namespace);
    }

    @GetMapping("/{namespace}/pods")
    public Map<String, Object> pods(@PathVariable String namespace) {
        return service.pods(namespace);
    }
}
