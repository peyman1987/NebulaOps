package dev.nebulaops.gitops;

import dev.nebulaops.gitops.service.GitOpsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/gitops")
public class GitOpsControlServiceController {
    private final GitOpsService service;

    public GitOpsControlServiceController(GitOpsService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> apps() {
        return service.apps();
    }

    @GetMapping("/{app}")
    public Map<String, Object> app(@PathVariable String app) {
        return service.app(app);
    }

    @PostMapping("/{app}/sync")
    public Map<String, Object> sync(@PathVariable String app) {
        return service.sync(app);
    }

    @PostMapping("/{app}/rollback/{revision}")
    public Map<String, Object> rollback(@PathVariable String app, @PathVariable String revision) {
        return service.rollback(app, revision);
    }
}
