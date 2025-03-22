package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.service.KubernetesPlatformService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/kubernetes")
public class KubernetesOpsController {
    private final KubernetesPlatformService service;

    public KubernetesOpsController(KubernetesPlatformService service) {
        this.service = service;
    }

    @GetMapping("/snapshot")
    public Map<String, Object> snapshot() {
        return service.cluster();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return service.nodes();
    }

    @GetMapping("/logs")
    public Map<String, Object> logs(@RequestParam(required = false) String namespace) {
        return service.events(namespace);
    }

    @GetMapping("/resources")
    public Map<String, Object> list(@RequestParam(defaultValue = "pods") String kind, @RequestParam(required = false) String namespace) {
        return service.resource(kind, namespace);
    }

    @GetMapping("/resources/{resourceId}")
    public Map<String, Object> get(@PathVariable String resourceId) {
        return service.resource(resourceId, null);
    }

    @GetMapping("/nodes")
    public Map<String, Object> nodes() {
        return service.nodes();
    }

    @GetMapping("/namespaces")
    public Map<String, Object> namespaces() {
        return service.namespaces();
    }

    @GetMapping("/{kind}")
    public Map<String, Object> resource(@PathVariable String kind, @RequestParam(required = false) String namespace) {
        return service.resource(kind, namespace);
    }
}
