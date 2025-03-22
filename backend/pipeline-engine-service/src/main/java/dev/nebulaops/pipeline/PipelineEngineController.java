package dev.nebulaops.pipeline;

import dev.nebulaops.pipeline.service.PipelineService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pipeline")
public class PipelineEngineController {
    private final PipelineService service;

    public PipelineEngineController(PipelineService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> current() {
        return service.current();
    }

    @GetMapping("/git-status")
    public Map<String, Object> gitStatus() {
        return service.gitStatus();
    }

    @PostMapping("/argocd/{app}/sync")
    public Map<String, Object> sync(@PathVariable String app) {
        return service.argoSync(app);
    }
}
