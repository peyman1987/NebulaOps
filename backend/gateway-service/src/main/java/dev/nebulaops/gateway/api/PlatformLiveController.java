package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/platform")
public class PlatformLiveController {
    private final ObservabilityPlatformService observability;
    private final GitOpsPlatformService gitops;
    private final SecurityPlatformService security;
    private final KubernetesPlatformService kubernetes;
    private final DockerRuntimeService docker;
    private final TerraformPlatformService terraform;

    public PlatformLiveController(ObservabilityPlatformService observability, GitOpsPlatformService gitops, SecurityPlatformService security, KubernetesPlatformService kubernetes, DockerRuntimeService docker, TerraformPlatformService terraform) {
        this.observability = observability;
        this.gitops = gitops;
        this.security = security;
        this.kubernetes = kubernetes;
        this.docker = docker;
        this.terraform = terraform;
    }

    @GetMapping("/observability")
    public Map<String, Object> observability() {
        return observability.stack();
    }

    @GetMapping("/observability/prometheus")
    public Map<String, Object> prometheus(@RequestParam(defaultValue = "up") String query) {
        return observability.prometheusQuery(query);
    }

    @GetMapping("/observability/loki")
    public Map<String, Object> loki(@RequestParam(required = false) String query) {
        return observability.lokiQuery(query);
    }

    @GetMapping("/gitops")
    public Map<String, Object> gitops() {
        return gitops.applications();
    }

    @PostMapping("/gitops/{app}/sync")
    public Map<String, Object> sync(@PathVariable String app) {
        return gitops.sync(app);
    }

    @PostMapping("/gitops/{app}/rollback/{revision}")
    public Map<String, Object> rollback(@PathVariable String app, @PathVariable String revision) {
        return gitops.rollback(app, revision);
    }

    @GetMapping("/devsecops")
    public Map<String, Object> devsecops(@RequestParam(defaultValue = ".") String path) {
        return security.trivyFs(path);
    }

    @GetMapping("/devsecops/secrets")
    public Map<String, Object> secrets(@RequestParam(defaultValue = ".") String path) {
        return security.secretScan(path);
    }

    @GetMapping("/environments")
    public Map<String, Object> environments() {
        return kubernetes.namespaces();
    }

    @GetMapping("/k8s/cluster")
    public Map<String, Object> cluster() {
        return kubernetes.cluster();
    }

    @GetMapping("/k8s/nodes")
    public Map<String, Object> nodes() {
        return kubernetes.nodes();
    }

    @GetMapping("/k8s/{kind}")
    public Map<String, Object> k8s(@PathVariable String kind, @RequestParam(required = false) String namespace) {
        return kubernetes.resource(kind, namespace);
    }

    @GetMapping("/helm/releases")
    public Map<String, Object> helm(@RequestParam(required = false) String namespace) {
        return kubernetes.helmReleases(namespace);
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

    @GetMapping("/docker/networks")
    public Map<String, Object> networks() {
        return docker.networks();
    }

    @GetMapping("/docker/builds")
    public Map<String, Object> builds() {
        return docker.builds();
    }

    @GetMapping("/docker/scout")
    public Map<String, Object> scout() {
        return docker.scout();
    }

    @GetMapping("/terraform/plan")
    public Map<String, Object> terraformPlan(@RequestParam(required = false) String workspace) {
        return terraform.plan(workspace);
    }

    @GetMapping("/terraform/graph")
    public Map<String, Object> terraformGraph(@RequestParam(required = false) String workspace) {
        return terraform.graph(workspace);
    }

    @GetMapping("/terraform/modules")
    public Map<String, Object> terraformModules(@RequestParam(required = false) String workspace) {
        return terraform.modules(workspace);
    }
}
