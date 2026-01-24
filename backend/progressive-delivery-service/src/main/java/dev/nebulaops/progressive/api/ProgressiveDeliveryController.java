package dev.nebulaops.progressive.api;

import dev.nebulaops.progressive.service.ProgressiveDeliveryService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/progressive-delivery")
public class ProgressiveDeliveryController {
    private final ProgressiveDeliveryService service;

    public ProgressiveDeliveryController(ProgressiveDeliveryService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> overview(@RequestParam(defaultValue = "all") String namespace) {
        return service.overview(namespace);
    }

    @GetMapping("/overview")
    public Map<String, Object> overviewAlias(@RequestParam(defaultValue = "all") String namespace) {
        return service.overview(namespace);
    }

    @GetMapping("/rollouts")
    public Map<String, Object> rollouts(@RequestParam(defaultValue = "all") String namespace) {
        return service.rollouts(namespace);
    }

    @GetMapping("/rollouts/{namespace}/{name}")
    public Map<String, Object> rollout(@PathVariable String namespace, @PathVariable String name) {
        return service.rollout(namespace, name);
    }

    @GetMapping("/analysis-runs")
    public Map<String, Object> analysisRuns(@RequestParam(defaultValue = "all") String namespace) {
        return service.analysisRuns(namespace);
    }

    @GetMapping("/experiments")
    public Map<String, Object> experiments(@RequestParam(defaultValue = "all") String namespace) {
        return service.experiments(namespace);
    }

    @GetMapping("/applications")
    public Map<String, Object> applications() {
        return service.applications();
    }

    @PostMapping("/applications/{app}/sync")
    public Map<String, Object> syncApplication(@PathVariable String app) {
        return service.syncApplication(app);
    }

    @PostMapping("/rollouts/{namespace}/{name}/promote")
    public Map<String, Object> promote(@PathVariable String namespace, @PathVariable String name,
                                       @RequestParam(defaultValue = "false") boolean full) {
        return service.promote(namespace, name, full);
    }

    @PostMapping("/rollouts/{namespace}/{name}/abort")
    public Map<String, Object> abort(@PathVariable String namespace, @PathVariable String name) {
        return service.abort(namespace, name);
    }

    @PostMapping("/rollouts/{namespace}/{name}/restart")
    public Map<String, Object> restart(@PathVariable String namespace, @PathVariable String name) {
        return service.restart(namespace, name);
    }

    @GetMapping("/rollouts/{namespace}/{name}/history")
    public Map<String, Object> history(@PathVariable String namespace, @PathVariable String name) {
        return service.history(namespace, name);
    }
}
