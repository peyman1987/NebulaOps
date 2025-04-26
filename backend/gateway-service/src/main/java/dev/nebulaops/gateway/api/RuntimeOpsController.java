package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * v21.1 — Runtime endpoints returning shapes Angular expects (plain Lists).
 */
@SuppressWarnings({"unchecked","rawtypes"})
@RestController
@RequestMapping("/api/runtime")
public class RuntimeOpsController {

    private final DockerRuntimeService docker;
    private final KubernetesPlatformService kubernetes;
    private final ObservabilityPlatformService observability;
    private final TerraformPlatformService terraform;

    public RuntimeOpsController(DockerRuntimeService docker,
                                KubernetesPlatformService kubernetes,
                                ObservabilityPlatformService observability,
                                TerraformPlatformService terraform) {
        this.docker = docker;
        this.kubernetes = kubernetes;
        this.observability = observability;
        this.terraform = terraform;
    }

    // Angular expects plain List — not {live, items, toolStatus}
    @GetMapping("/docker/containers")
    public List containers() { return items(docker.containers()); }

    @GetMapping("/docker/images")
    public List images() { return items(docker.images()); }

    @GetMapping("/docker/volumes")
    public List volumes() { return items(docker.volumes()); }

    @GetMapping("/docker/stats")
    public List stats() { return items(docker.stats()); }

    @GetMapping("/docker/networks")
    public List networks() { return items(docker.networks()); }

    @GetMapping("/docker/builds")
    public List builds() { return items(docker.builds()); }

    // Angular expects plain List
    @GetMapping("/helm/releases")
    public List helm(@RequestParam(required = false) String namespace) {
        Map raw = kubernetes.helmReleases(namespace);
        Object data = raw.get("data");
        return data instanceof List ? (List) data : Collections.emptyList();
    }

    @GetMapping("/grafana/health")
    public Map<String, Object> grafana() { return observability.grafanaHealth(); }

    @GetMapping("/terraform/validate")
    public Map<String, Object> terraformValidate() { return terraform.modules(null); }

    @GetMapping("/terraform/plan")
    public Map<String, Object> terraformPlan(@RequestParam(required = false) String workspace) {
        return terraform.plan(workspace);
    }

    private List items(Map raw) {
        Object i = raw.get("items");
        if (i instanceof List) return (List) i;
        Object d = raw.get("data");
        if (d instanceof List) return (List) d;
        return Collections.emptyList();
    }
}
