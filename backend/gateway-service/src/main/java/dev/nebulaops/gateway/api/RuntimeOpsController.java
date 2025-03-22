package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/runtime")
public class RuntimeOpsController {
    private final DockerRuntimeService docker;
    private final KubernetesPlatformService kubernetes;
    private final ObservabilityPlatformService observability;
    private final TerraformPlatformService terraform;

    public RuntimeOpsController(DockerRuntimeService docker, KubernetesPlatformService kubernetes, ObservabilityPlatformService observability, TerraformPlatformService terraform) {
        this.docker = docker;
        this.kubernetes = kubernetes;
        this.observability = observability;
        this.terraform = terraform;
    }

    @GetMapping("/docker/containers")
    public Map<String, Object> containers() {
        return docker.containers();
    }

    @GetMapping("/docker/images")
    public Map<String, Object> images() {
        return docker.images();
    }

    @GetMapping("/docker/volumes")
    public Map<String, Object> volumes() {
        return docker.volumes();
    }

    @GetMapping("/docker/stats")
    public Map<String, Object> stats() {
        return docker.stats();
    }

    @GetMapping("/helm/releases")
    public Map<String, Object> helm(@RequestParam(required = false) String namespace) {
        return kubernetes.helmReleases(namespace);
    }

    @GetMapping("/grafana/health")
    public Map<String, Object> grafana() {
        return observability.grafanaHealth();
    }

    @GetMapping("/terraform/validate")
    public Map<String, Object> terraformValidate() {
        return terraform.modules(null);
    }

    @GetMapping("/terraform/plan")
    public Map<String, Object> terraformPlan(@RequestParam(required = false) String workspace) {
        return terraform.plan(workspace);
    }
}
