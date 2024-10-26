package dev.nebulaops.gitops;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/gitops")
public class GitOpsControlServiceController {
    @GetMapping("/state")
    public Map<String, Object> state() {
        return Map.of("sync", "OutOfSync", "drift", 3, "revision", "a19f5c2", "health", "Degraded");
    }

    @PostMapping("/sync")
    public Map<String, Object> sync() {
        return Map.of("status", "sync-requested", "tool", "ArgoCD", "requestedAt", Instant.now().toString());
    }

    @PostMapping("/rollback")
    public Map<String, Object> rollback() {
        return Map.of("status", "rollback-ready", "strategy", "visual-gated");
    }
}