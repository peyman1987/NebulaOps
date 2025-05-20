package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * v21.2 — Platform endpoints returning shapes Angular expects.
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

    /** v21.2.1 — Returns shape: { live, scans, cves, controls, threats } matching Angular interfaces. */
    @GetMapping("/devsecops")
    public Map<String, Object> devsecopsEnriched(@RequestParam(defaultValue = ".") String path) {
        Map trivyRaw = security.trivyFs(path);
        boolean live = Boolean.TRUE.equals(trivyRaw.get("live"));

        List<Map<String, Object>> scans = new ArrayList<>();
        List<Map<String, Object>> cves  = new ArrayList<>();
        int critSum = 0, highSum = 0, medSum = 0;

        Object trivyData = trivyRaw.get("data");
        if (trivyData instanceof Map) {
            Object results = ((Map) trivyData).get("Results");
            if (results instanceof List) {
                for (Object res : (List) results) {
                    if (!(res instanceof Map)) continue;
                    Map result = (Map) res;
                    String target = str(result.getOrDefault("Target", "unknown"));
                    Object vulns  = result.get("Vulnerabilities");
                    int c = 0, h = 0, m = 0;
                    if (vulns instanceof List) {
                        for (Object v : (List) vulns) {
                            if (!(v instanceof Map)) continue;
                            Map vuln = (Map) v;
                            String sev = str(vuln.getOrDefault("Severity", "UNKNOWN")).toUpperCase();
                            if ("CRITICAL".equals(sev)) c++;
                            else if ("HIGH".equals(sev)) h++;
                            else if ("MEDIUM".equals(sev)) m++;

                            Map<String, Object> cve = new LinkedHashMap<>();
                            cve.put("cve",         str(vuln.getOrDefault("VulnerabilityID", "CVE-UNKNOWN")));
                            cve.put("packageName", str(vuln.getOrDefault("PkgName", "")));
                            cve.put("severity",    sev);
                            cve.put("image",       target);
                            cve.put("fixVersion",  str(vuln.getOrDefault("FixedVersion", "n/a")));
                            cve.put("exploit",     str(vuln.getOrDefault("Title", "")));
                            cves.add(cve);
                        }
                        critSum += c; highSum += h; medSum += m;
                        Map<String, Object> scan = new LinkedHashMap<>();
                        scan.put("id",       "scan-" + scans.size());
                        scan.put("tool",     "Trivy");
                        scan.put("target",   target);
                        scan.put("status",   (c > 0 ? "failed" : (h > 0 ? "running" : "passed")));
                        scan.put("critical", c);
                        scan.put("high",     h);
                        scan.put("medium",   m);
                        scan.put("duration", "live");
                        scans.add(scan);
                    }
                }
            }
        }

        // If no live data, expose a clear placeholder scan so the UI shows tool state
        if (scans.isEmpty()) {
            scans.add(Map.of(
                "id", "scan-trivy-status",
                "tool", "Trivy",
                "target", path,
                "status", live ? "passed" : "queued",
                "critical", 0, "high", 0, "medium", 0,
                "duration", live ? "0s" : "not-run"
            ));
        }

        // Synthesize compliance controls from scan results (CIS-flavored, derived from real data)
        List<Map<String, Object>> controls = new ArrayList<>();
        controls.add(controlOf("CIS-DI-0001", "CIS Docker",  "No critical vulnerabilities in scanned images", critSum == 0 ? 100 : 0,    critSum == 0 ? "pass" : "fail"));
        controls.add(controlOf("CIS-DI-0002", "CIS Docker",  "Limit high severity findings to acceptable levels", highSum <= 3 ? 100 : 60, highSum <= 3 ? "pass" : "warn"));
        controls.add(controlOf("NIST-SI-2",   "NIST 800-53", "Flaw remediation — track and fix CVEs",            cves.isEmpty() ? 100 : Math.max(30, 100 - cves.size() * 5), cves.size() < 10 ? "pass" : "warn"));
        controls.add(controlOf("SOC2-CC7-1",  "SOC2",        "System monitoring — live scan tool reachable",      live ? 100 : 0,            live ? "pass" : "fail"));

        // Threat radar — derive from real CVE counts (geometric positioning around the radar)
        List<Map<String, Object>> threats = new ArrayList<>();
        int idx = 0;
        for (Map<String, Object> cve : cves) {
            if (idx >= 6) break;
            double angle = (idx * 60.0) * Math.PI / 180.0;
            double radius = "CRITICAL".equals(cve.get("severity")) ? 30 : "HIGH".equals(cve.get("severity")) ? 50 : 70;
            threats.add(Map.of(
                "name",     cve.get("cve"),
                "x",        50 + radius * Math.cos(angle) / 100.0 * 50,
                "y",        50 + radius * Math.sin(angle) / 100.0 * 50,
                "severity", cve.get("severity"),
                "vector",   cve.get("packageName")
            ));
            idx++;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live",       live);
        out.put("scans",      scans);
        out.put("cves",       cves);
        out.put("controls",   controls);
        out.put("threats",    threats);
        out.put("toolStatus", trivyRaw.get("toolStatus"));
        return out;
    }

    private Map<String, Object> controlOf(String id, String framework, String title, int score, String status) {
        return Map.of("id", id, "framework", framework, "title", title, "score", score, "status", status);
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
