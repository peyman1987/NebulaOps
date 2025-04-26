package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * v21.1 — Platform endpoints returning shapes Angular expects.
 */
@SuppressWarnings({"unchecked","rawtypes"})
@RestController
@RequestMapping("/api/platform")
public class PlatformLiveController {

    private final ObservabilityPlatformService observability;
    private final GitOpsPlatformService gitops;
    private final SecurityPlatformService security;
    private final KubernetesPlatformService kubernetes;
    private final DockerRuntimeService docker;
    private final TerraformPlatformService terraform;

    public PlatformLiveController(ObservabilityPlatformService observability,
                                  GitOpsPlatformService gitops,
                                  SecurityPlatformService security,
                                  KubernetesPlatformService kubernetes,
                                  DockerRuntimeService docker,
                                  TerraformPlatformService terraform) {
        this.observability = observability;
        this.gitops = gitops;
        this.security = security;
        this.kubernetes = kubernetes;
        this.docker = docker;
        this.terraform = terraform;
    }

    /** Angular requires traceFlow, latencyHeatmap, eventStream fields */
    @GetMapping("/observability")
    public Map<String, Object> observabilityEnriched() {
        Map<String, Object> out = new LinkedHashMap<>(observability.stack());
        out.putIfAbsent("traceFlow",      Collections.emptyList());
        out.putIfAbsent("latencyHeatmap", Collections.emptyList());
        out.putIfAbsent("eventStream",    Collections.emptyList());
        return out;
    }

    @GetMapping("/observability/prometheus")
    public Map<String, Object> prometheus(@RequestParam(defaultValue = "up") String query) {
        return observability.prometheusQuery(query);
    }

    @GetMapping("/observability/loki")
    public Map<String, Object> loki(@RequestParam(required = false) String query) {
        return observability.lokiQuery(query);
    }

    /** Angular expects { live, state, deploymentWaves, commitStream, tool } */
    @GetMapping("/gitops")
    public Map<String, Object> gitopsEnriched() {
        Map raw  = gitops.applications();
        boolean live = Boolean.TRUE.equals(raw.get("live"));
        List<Map<String, Object>> waves = new ArrayList<>();
        Object data = raw.get("data");
        if (data instanceof List) {
            for (Object app : (List) data) {
                if (!(app instanceof Map)) continue;
                Map a      = (Map) app;
                Map status = asMap(a.get("status"));
                Map health = asMap(status.get("health"));
                Map sync   = asMap(status.get("sync"));
                Map meta   = asMap(a.get("metadata"));
                Map<String, Object> wave = new LinkedHashMap<>();
                wave.put("app",    str(meta.getOrDefault("name", "app")));
                wave.put("health", str(health.getOrDefault("status", "Unknown")));
                wave.put("sync",   str(sync.getOrDefault("status", "Unknown")));
                wave.put("live",   true);
                waves.add(wave);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live",            live);
        out.put("state",           live && !waves.isEmpty() ? "Synced" : live ? "NoApps" : "Disconnected");
        out.put("deploymentWaves", waves);
        out.put("commitStream",    Collections.emptyList());
        out.put("tool",            "argocd");
        out.put("toolStatus",      raw.get("toolStatus"));
        return out;
    }

    @PostMapping("/gitops/{app}/sync")
    public Map<String, Object> sync(@PathVariable String app) { return gitops.sync(app); }

    @PostMapping("/gitops/{app}/rollback/{revision}")
    public Map<String, Object> rollback(@PathVariable String app, @PathVariable String revision) {
        return gitops.rollback(app, revision);
    }

    /** Angular expects { live, scans, cves, controls, threats } */
    @GetMapping("/devsecops")
    public Map<String, Object> devsecopsEnriched(@RequestParam(defaultValue = ".") String path) {
        Map trivyRaw = security.trivyFs(path);
        boolean live = Boolean.TRUE.equals(trivyRaw.get("live"));
        List<Map<String, Object>> scans = new ArrayList<>();
        List<Map<String, Object>> cves  = new ArrayList<>();
        Object trivyData = trivyRaw.get("data");
        if (trivyData instanceof Map) {
            Object results = ((Map) trivyData).get("Results");
            if (results instanceof List) {
                for (Object res : (List) results) {
                    if (!(res instanceof Map)) continue;
                    Map result = (Map) res;
                    String target = str(result.getOrDefault("Target", "unknown"));
                    Object vulns  = result.get("Vulnerabilities");
                    if (vulns instanceof List) {
                        Map<String, Object> scan = new LinkedHashMap<>();
                        scan.put("target", target);
                        scan.put("count",  ((List) vulns).size());
                        scan.put("live",   true);
                        scans.add(scan);
                        for (Object v : (List) vulns) {
                            if (!(v instanceof Map)) continue;
                            Map vuln = (Map) v;
                            Map<String, Object> cve = new LinkedHashMap<>();
                            cve.put("id",       str(vuln.getOrDefault("VulnerabilityID", "")));
                            cve.put("severity", str(vuln.getOrDefault("Severity", "UNKNOWN")));
                            cve.put("pkg",      str(vuln.getOrDefault("PkgName", "")));
                            cve.put("title",    str(vuln.getOrDefault("Title", "")));
                            cves.add(cve);
                        }
                    }
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live",       live);
        out.put("scans",      scans);
        out.put("cves",       cves);
        out.put("controls",   Collections.emptyList());
        out.put("threats",    Collections.emptyList());
        out.put("toolStatus", trivyRaw.get("toolStatus"));
        return out;
    }

    @GetMapping("/devsecops/secrets")
    public Map<String, Object> secrets(@RequestParam(defaultValue = ".") String path) {
        return security.secretScan(path);
    }

    /** Angular expects plain List of { name, phase, live, createdAt } */
    @GetMapping("/environments")
    public List<Map<String, Object>> environmentsList() {
        Map raw = kubernetes.namespaces();
        List<Map<String, Object>> result = new ArrayList<>();
        Object data = raw.get("data");
        if (data instanceof Map) {
            Object items = ((Map) data).get("items");
            if (items instanceof List) {
                for (Object ns : (List) items) {
                    if (!(ns instanceof Map)) continue;
                    Map n      = (Map) ns;
                    Map meta   = asMap(n.get("metadata"));
                    Map status = asMap(n.get("status"));
                    Map<String, Object> env = new LinkedHashMap<>();
                    env.put("name",      str(meta.getOrDefault("name", "namespace")));
                    env.put("phase",     str(status.getOrDefault("phase", "Active")));
                    env.put("live",      Boolean.TRUE.equals(raw.get("live")));
                    env.put("createdAt", str(meta.getOrDefault("creationTimestamp", "")));
                    result.add(env);
                }
            }
        }
        return result;
    }

    @GetMapping("/k8s/cluster")
    public Map<String, Object> cluster() { return kubernetes.cluster(); }

    @GetMapping("/k8s/nodes")
    public Map<String, Object> nodes() { return kubernetes.nodes(); }

    @GetMapping("/k8s/{kind}")
    public Map<String, Object> k8s(@PathVariable String kind,
                                   @RequestParam(required = false) String namespace) {
        return kubernetes.resource(kind, namespace);
    }

    @GetMapping("/helm/releases")
    public Map<String, Object> helm(@RequestParam(required = false) String namespace) {
        return kubernetes.helmReleases(namespace);
    }

    @GetMapping("/docker/containers")
    public Map<String, Object> containers() { return docker.containers(); }

    @GetMapping("/docker/images")
    public Map<String, Object> images() { return docker.images(); }

    @GetMapping("/docker/volumes")
    public Map<String, Object> volumes() { return docker.volumes(); }

    @GetMapping("/docker/networks")
    public Map<String, Object> networks() { return docker.networks(); }

    @GetMapping("/docker/builds")
    public Map<String, Object> builds() { return docker.builds(); }

    @GetMapping("/docker/scout")
    public Map<String, Object> scout() { return docker.scout(); }

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

    private Map asMap(Object o) { return o instanceof Map ? (Map) o : Collections.emptyMap(); }
    private String str(Object o) { return o == null ? "" : String.valueOf(o); }
}
