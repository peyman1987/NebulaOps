package dev.nebulaops.aiops;

import dev.nebulaops.aiops.service.AiOpsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/aiops", "/api/ai-ops"})
public class AiOpsController {
    private final AiOpsService service;

    public AiOpsController(AiOpsService service) {
        this.service = service;
    }

    @GetMapping("/diagnose")
    public Map<String, Object> diagnose(@RequestParam(required = false) String namespace) {
        return service.diagnose(namespace);
    }

    @PostMapping("/analyze")
    public Map<String, Object> analyze(@RequestBody(required = false) Map<String, Object> body) {
        return service.analyze(body == null ? Map.of() : body);
    }

    @PostMapping("/autofix")
    public Map<String, Object> autofix(@RequestBody(required = false) Map<String, Object> body) {
        return service.autofix(body == null ? Map.of() : body);
    }

    @GetMapping("/logs")
    public Map<String, Object> logs(@RequestParam(required = false) String selector, @RequestParam(defaultValue = "default") String namespace) {
        return service.logs(selector, namespace);
    }

    @PostMapping("/actions/{action}")
    public Map<String, Object> action(@PathVariable String action, @RequestParam String deployment, @RequestParam(defaultValue = "default") String namespace) {
        return service.action(namespace, deployment, action);
    }
}
