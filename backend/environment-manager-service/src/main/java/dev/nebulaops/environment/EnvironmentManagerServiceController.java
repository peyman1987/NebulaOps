package dev.nebulaops.environment;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/environments")
public class EnvironmentManagerServiceController {
    @GetMapping
    public List<Map<String, Object>> environments() {
        return List.of(Map.of("name", "LOCAL", "namespace", "nebulaops-local", "workspace", "local"), Map.of("name", "DEV", "namespace", "nebulaops-dev", "workspace", "dev"), Map.of("name", "STAGING", "namespace", "nebulaops-staging", "workspace", "staging"), Map.of("name", "PROD", "namespace", "nebulaops-prod", "workspace", "prod"));
    }

    @PostMapping("/{name}/provision")
    public Map<String, Object> provision(@PathVariable String name) {
        return Map.of("status", "provisioning", "environment", name, "backend", "terraform-workspaces");
    }
}