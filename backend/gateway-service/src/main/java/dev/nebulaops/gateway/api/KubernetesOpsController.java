package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.client.DockerSocketClient;
import dev.nebulaops.gateway.client.ToolCommandClient;
import dev.nebulaops.gateway.client.ToolResult;
import dev.nebulaops.gateway.service.KubernetesPlatformService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * v22.1 — Kubernetes controller: snapshot/fallback + all OpenLens actions.
 *
 * READ endpoints (no conflict with actions):
 *   GET /api/kubernetes/snapshot
 *   GET /api/kubernetes/logs
 *   GET /api/kubernetes/health
 *   GET /api/kubernetes/nodes
 *   GET /api/kubernetes/namespaces
 *   GET /api/kubernetes/resources
 *   GET /api/kubernetes/resources/{id}
 *   GET /api/kubernetes/kind/{kind}           ← renamed from /{kind} to avoid ambiguity
 *
 * ACTION endpoints (all return { ok, action, target, stdout, stderr, exitCode }):
 *   DELETE /api/kubernetes/pods/{ns}/{name}
 *   POST   /api/kubernetes/pods/{ns}/{name}/restart
 *   POST   /api/kubernetes/pods/{ns}/{name}/logs
 *   GET    /api/kubernetes/pods/{ns}/{name}/describe
 *   POST   /api/kubernetes/deployments/{ns}/{name}/scale
 *   POST   /api/kubernetes/deployments/{ns}/{name}/restart
 *   GET    /api/kubernetes/deployments/{ns}/{name}/yaml
 *   POST   /api/kubernetes/deployments/{ns}/{name}/yaml
 *   GET    /api/kubernetes/deployments/{ns}/{name}/describe
 *   GET    /api/kubernetes/services/{ns}/{name}/yaml
 *   POST   /api/kubernetes/services/{ns}/{name}/yaml
 *   GET    /api/kubernetes/services/{ns}/{name}/describe
 *   GET    /api/kubernetes/ingresses/{ns}/{name}/yaml
 *   POST   /api/kubernetes/ingresses/{ns}/{name}/yaml
 *   GET    /api/kubernetes/ingresses/{ns}/{name}/describe
 *   POST   /api/kubernetes/statefulsets/{ns}/{name}/scale
 *   POST   /api/kubernetes/statefulsets/{ns}/{name}/restart
 *   GET    /api/kubernetes/configmaps/{ns}/{name}/yaml
 *   POST   /api/kubernetes/configmaps/{ns}/{name}/yaml
 *   POST   /api/kubernetes/nodes/{name}/cordon
 *   POST   /api/kubernetes/nodes/{name}/uncordon
 *   POST   /api/kubernetes/nodes/{name}/drain
 *   GET    /api/kubernetes/nodes/{name}/describe
 *   POST   /api/kubernetes/namespaces          (create)
 *   GET    /api/kubernetes/namespaces/{name}/describe
 *   POST   /api/kubernetes/apply
 *   POST   /api/kubernetes/delete
 */
@SuppressWarnings({"unchecked","rawtypes"})
@RestController
@RequestMapping("/api/kubernetes")
public class KubernetesOpsController {

    private final KubernetesPlatformService service;
    private final DockerSocketClient        dockerSocket;
    private final ToolCommandClient         tools;

    public KubernetesOpsController(KubernetesPlatformService service,
                                   DockerSocketClient dockerSocket,
                                   ToolCommandClient tools) {
        this.service      = service;
        this.dockerSocket = dockerSocket;
        this.tools        = tools;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // READ / SNAPSHOT ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/snapshot")
    public Map<String, Object> snapshot() {
        Map nodesRaw  = service.nodes();
        boolean connected = Boolean.TRUE.equals(nodesRaw.get("live"));
        List<Map<String, Object>> resources = connected
                ? buildResourcesFromKubectl(nodesRaw)
                : buildResourcesFromDocker();

        Map<String, Object> cluster = new LinkedHashMap<>();
        cluster.put("name",        connected ? "kind-nebulaops" : "docker-compose");
        cluster.put("provider",    connected ? "kind" : "docker-compose");
        cluster.put("version",     "v1.29");
        cluster.put("status",      "Connected");
        cluster.put("live",        true);
        cluster.put("generatedAt", Instant.now().toString());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cluster",   cluster);
        out.put("resources", resources);
        out.put("logs",      buildLogsFromDocker());
        out.put("live",      true);
        return out;
    }

    @GetMapping("/logs")
    public List<Map<String, Object>> logs(@RequestParam(required = false) String namespace) {
        Map raw = service.events(namespace);
        List<Map<String, Object>> result = new ArrayList<>();
        if (Boolean.TRUE.equals(raw.get("live"))) {
            Object data = raw.get("data");
            if (data instanceof Map) {
                Object items = ((Map) data).get("items");
                if (items instanceof List) {
                    for (Object ev : (List) items) {
                        if (!(ev instanceof Map)) continue;
                        Map event  = (Map) ev;
                        Map involv = asMap(event.get("involvedObject"));
                        Map<String, Object> log = new LinkedHashMap<>();
                        log.put("time",    str(event.getOrDefault("lastTimestamp", Instant.now().toString())));
                        log.put("service", str(involv.getOrDefault("name", "kubernetes")));
                        log.put("level",   "Warning".equals(event.get("type")) ? "WARN" : "INFO");
                        log.put("message", str(event.getOrDefault("message", "")));
                        result.add(log);
                    }
                }
            }
        }
        result.addAll(buildLogsFromDocker());
        return result;
    }

    @GetMapping("/health")
    public Map<String, Object> health() { return service.nodes(); }

    @GetMapping("/nodes")
    public Map<String, Object> nodes() { return service.nodes(); }

    @GetMapping("/namespaces")
    public Map<String, Object> namespaces() { return service.namespaces(); }

    @GetMapping("/resources")
    public Map<String, Object> list(@RequestParam(defaultValue = "pods") String kind,
                                    @RequestParam(required = false) String namespace) {
        return service.resource(kind, namespace);
    }

    @GetMapping("/resources/{resourceId}")
    public Map<String, Object> getResource(@PathVariable String resourceId) {
        return service.resource(resourceId, null);
    }

    /** Renamed from /{kind} to /kind/{kind} to avoid ambiguity with action paths */
    @GetMapping("/kind/{kind}")
    public Map<String, Object> resource(@PathVariable String kind,
                                        @RequestParam(required = false) String namespace) {
        return service.resource(kind, namespace);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // POD ACTIONS
    // ═══════════════════════════════════════════════════════════════════════

    @DeleteMapping("/pods/{ns}/{name}")
    public Map<String, Object> deletePod(@PathVariable String ns, @PathVariable String name,
            @RequestParam(defaultValue = "false") boolean force) {
        String cmd = force
            ? "kubectl delete pod " + safe(name) + " -n " + safe(ns) + " --grace-period=0 --force"
            : "kubectl delete pod " + safe(name) + " -n " + safe(ns);
        return run(cmd, "delete-pod", ns + "/" + name, 30);
    }

    @PostMapping("/pods/{ns}/{name}/restart")
    public Map<String, Object> restartPod(@PathVariable String ns, @PathVariable String name) {
        ToolResult r = tools.shell(
            "kubectl get pod " + safe(name) + " -n " + safe(ns)
            + " -o jsonpath='{.metadata.ownerReferences[0].kind}/{.metadata.ownerReferences[0].name}'", 10);
        if (r.ok() && !r.stdout().isBlank()) {
            String owner = r.stdout().trim().replace("'", "");
            if (owner.startsWith("ReplicaSet/")) {
                String rsName = owner.substring("ReplicaSet/".length());
                ToolResult rd = tools.shell(
                    "kubectl get replicaset " + rsName + " -n " + safe(ns)
                    + " -o jsonpath='{.metadata.ownerReferences[0].name}'", 8);
                if (rd.ok() && !rd.stdout().isBlank()) {
                    return run("kubectl rollout restart deployment/" + rd.stdout().trim().replace("'","")
                               + " -n " + safe(ns), "restart-pod", ns + "/" + name, 30);
                }
            }
            if (owner.startsWith("StatefulSet/")) {
                return run("kubectl rollout restart statefulset/" + owner.substring("StatefulSet/".length())
                           + " -n " + safe(ns), "restart-pod", ns + "/" + name, 30);
            }
        }
        return run("kubectl delete pod " + safe(name) + " -n " + safe(ns),
                   "restart-pod", ns + "/" + name, 20);
    }

    @GetMapping("/pods/{ns}/{name}/logs")
    public Map<String, Object> podLogs(@PathVariable String ns, @PathVariable String name,
            @RequestParam(defaultValue = "100") int tail,
            @RequestParam(required = false) String container) {
        String cmd = "kubectl logs " + safe(name) + " -n " + safe(ns) + " --tail=" + tail
                   + (container != null ? " -c " + safe(container) : "");
        return run(cmd, "pod-logs", ns + "/" + name, 15);
    }

    @GetMapping("/pods/{ns}/{name}/describe")
    public Map<String, Object> describePod(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl describe pod " + safe(name) + " -n " + safe(ns), "describe-pod", ns + "/" + name, 15);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DEPLOYMENT ACTIONS
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/deployments/{ns}/{name}/scale")
    public Map<String, Object> scaleDeployment(@PathVariable String ns, @PathVariable String name,
            @RequestBody Map<String, Object> body) {
        int r = Math.max(0, Math.min(20, ((Number) body.getOrDefault("replicas", 1)).intValue()));
        return run("kubectl scale deployment/" + safe(name) + " -n " + safe(ns) + " --replicas=" + r,
                   "scale-deployment", ns + "/" + name, 20);
    }

    @PostMapping("/deployments/{ns}/{name}/restart")
    public Map<String, Object> restartDeployment(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl rollout restart deployment/" + safe(name) + " -n " + safe(ns),
                   "restart-deployment", ns + "/" + name, 30);
    }

    @GetMapping("/deployments/{ns}/{name}/yaml")
    public Map<String, Object> deploymentYaml(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl get deployment " + safe(name) + " -n " + safe(ns) + " -o yaml",
                   "get-yaml", ns + "/" + name, 10);
    }

    @PostMapping("/deployments/{ns}/{name}/yaml")
    public Map<String, Object> applyDeploymentYaml(@PathVariable String ns, @PathVariable String name,
            @RequestBody Map<String, Object> body) {
        return applyYaml((String) body.get("yaml"), "apply-deployment", ns + "/" + name);
    }

    @GetMapping("/deployments/{ns}/{name}/describe")
    public Map<String, Object> describeDeployment(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl describe deployment " + safe(name) + " -n " + safe(ns), "describe", ns + "/" + name, 15);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SERVICE ACTIONS
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/services/{ns}/{name}/yaml")
    public Map<String, Object> serviceYaml(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl get service " + safe(name) + " -n " + safe(ns) + " -o yaml", "get-yaml", ns + "/" + name, 10);
    }

    @PostMapping("/services/{ns}/{name}/yaml")
    public Map<String, Object> applyServiceYaml(@PathVariable String ns, @PathVariable String name,
            @RequestBody Map<String, Object> body) {
        return applyYaml((String) body.get("yaml"), "apply-service", ns + "/" + name);
    }

    @GetMapping("/services/{ns}/{name}/describe")
    public Map<String, Object> describeService(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl describe service " + safe(name) + " -n " + safe(ns), "describe", ns + "/" + name, 15);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INGRESS ACTIONS
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/ingresses/{ns}/{name}/yaml")
    public Map<String, Object> ingressYaml(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl get ingress " + safe(name) + " -n " + safe(ns) + " -o yaml", "get-yaml", ns + "/" + name, 10);
    }

    @PostMapping("/ingresses/{ns}/{name}/yaml")
    public Map<String, Object> applyIngressYaml(@PathVariable String ns, @PathVariable String name,
            @RequestBody Map<String, Object> body) {
        return applyYaml((String) body.get("yaml"), "apply-ingress", ns + "/" + name);
    }

    @GetMapping("/ingresses/{ns}/{name}/describe")
    public Map<String, Object> describeIngress(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl describe ingress " + safe(name) + " -n " + safe(ns), "describe", ns + "/" + name, 15);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STATEFULSET ACTIONS
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/statefulsets/{ns}/{name}/scale")
    public Map<String, Object> scaleStatefulSet(@PathVariable String ns, @PathVariable String name,
            @RequestBody Map<String, Object> body) {
        int r = Math.max(0, Math.min(20, ((Number) body.getOrDefault("replicas",1)).intValue()));
        return run("kubectl scale statefulset/" + safe(name) + " -n " + safe(ns) + " --replicas=" + r,
                   "scale-statefulset", ns + "/" + name, 20);
    }

    @PostMapping("/statefulsets/{ns}/{name}/restart")
    public Map<String, Object> restartStatefulSet(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl rollout restart statefulset/" + safe(name) + " -n " + safe(ns),
                   "restart-statefulset", ns + "/" + name, 30);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONFIGMAP ACTIONS
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/configmaps/{ns}/{name}/yaml")
    public Map<String, Object> configMapYaml(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl get configmap " + safe(name) + " -n " + safe(ns) + " -o yaml", "get-yaml", ns + "/" + name, 10);
    }

    @PostMapping("/configmaps/{ns}/{name}/yaml")
    public Map<String, Object> applyConfigMapYaml(@PathVariable String ns, @PathVariable String name,
            @RequestBody Map<String, Object> body) {
        return applyYaml((String) body.get("yaml"), "apply-configmap", ns + "/" + name);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // NODE ACTIONS
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/nodes/{name}/cordon")
    public Map<String, Object> cordonNode(@PathVariable String name) {
        return run("kubectl cordon " + safe(name), "cordon", name, 15);
    }

    @PostMapping("/nodes/{name}/uncordon")
    public Map<String, Object> uncordonNode(@PathVariable String name) {
        return run("kubectl uncordon " + safe(name), "uncordon", name, 15);
    }

    @PostMapping("/nodes/{name}/drain")
    public Map<String, Object> drainNode(@PathVariable String name) {
        return run("kubectl drain " + safe(name) + " --ignore-daemonsets --delete-emptydir-data",
                   "drain", name, 120);
    }

    @GetMapping("/nodes/{name}/describe")
    public Map<String, Object> describeNode(@PathVariable String name) {
        return run("kubectl describe node " + safe(name), "describe-node", name, 15);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // NAMESPACE ACTIONS
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/namespaces")
    public Map<String, Object> createNamespace(@RequestBody Map<String, Object> body) {
        String name = safe((String) body.getOrDefault("name", ""));
        if (name.isEmpty()) return Map.of("ok", false, "error", "name required");
        return run("kubectl create namespace " + name, "create-namespace", name, 10);
    }

    @GetMapping("/namespaces/{name}/describe")
    public Map<String, Object> describeNamespace(@PathVariable String name) {
        return run("kubectl describe namespace " + safe(name), "describe-namespace", name, 10);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GENERIC APPLY / DELETE
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/apply")
    public Map<String, Object> applyManifest(@RequestBody Map<String, Object> body) {
        return applyYaml((String) body.get("yaml"), "apply", "manifest");
    }

    @PostMapping("/delete")
    public Map<String, Object> deleteManifest(@RequestBody Map<String, Object> body) {
        return applyYaml((String) body.get("yaml"), "delete", "manifest");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DOCKER FALLBACK BUILDERS
    // ═══════════════════════════════════════════════════════════════════════

    private List<Map<String, Object>> buildResourcesFromKubectl(Map nodesRaw) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object data = nodesRaw.get("data");
        if (!(data instanceof Map)) return buildResourcesFromDocker();
        Object itemsObj = ((Map) data).get("items");
        if (!(itemsObj instanceof List)) return buildResourcesFromDocker();
        for (Object item : (List) itemsObj) {
            if (!(item instanceof Map)) continue;
            Map node   = (Map) item;
            Map meta   = asMap(node.get("metadata"));
            Map status = asMap(node.get("status"));
            String ready = "Unknown";
            Object conds = status.get("conditions");
            if (conds instanceof List) {
                for (Object c : (List) conds) {
                    if (c instanceof Map && "Ready".equals(((Map) c).get("type")))
                        ready = "True".equals(((Map) c).get("status")) ? "Running" : "NotReady";
                }
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id",        str(meta.getOrDefault("uid", UUID.randomUUID().toString())));
            r.put("kind",      "Node");
            r.put("namespace", "");
            r.put("name",      str(meta.getOrDefault("name", "node")));
            r.put("replicas",  1);
            r.put("status",    ready);
            r.put("yaml",      "# kubectl get node " + meta.getOrDefault("name","") + " -o yaml");
            r.put("updatedAt", str(meta.getOrDefault("creationTimestamp", Instant.now().toString())));
            result.add(r);
        }
        return result.isEmpty() ? buildResourcesFromDocker() : result;
    }

    private List<Map<String, Object>> buildResourcesFromDocker() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> containers = dockerSocket.containers();
        for (Map<String, Object> c : containers) {
            String name  = containerName(c);
            String image = str(c.getOrDefault("Image", "-"));
            String state = str(c.getOrDefault("State", "unknown"));
            String ports = portsOf(c);
            boolean running = "running".equals(state);
            String ns = "nebulaops";

            Map<String, Object> dep = new LinkedHashMap<>();
            dep.put("id",        "Deployment:" + ns + ":" + name);
            dep.put("kind",      "Deployment");
            dep.put("namespace", ns);
            dep.put("name",      name);
            dep.put("replicas",  running ? 1 : 0);
            dep.put("status",    running ? "Available" : "Unavailable");
            dep.put("yaml",      deploymentYamlText(name, image, ns));
            dep.put("updatedAt", Instant.now().toString());
            result.add(dep);

            Map<String, Object> pod = new LinkedHashMap<>();
            pod.put("id",        "Pod:" + ns + ":" + name + "-pod");
            pod.put("kind",      "Pod");
            pod.put("namespace", ns);
            pod.put("name",      name + "-pod");
            pod.put("replicas",  1);
            pod.put("status",    running ? "Running" : "Stopped");
            pod.put("yaml",      "# pod derived from container: " + name);
            pod.put("updatedAt", Instant.now().toString());
            result.add(pod);

            if (!ports.isEmpty() && !ports.equals("-")) {
                Map<String, Object> svc = new LinkedHashMap<>();
                svc.put("id",        "Service:" + ns + ":" + name);
                svc.put("kind",      "Service");
                svc.put("namespace", ns);
                svc.put("name",      name);
                svc.put("replicas",  0);
                svc.put("status",    "Active");
                svc.put("yaml",      serviceYamlText(name, ns, ports));
                svc.put("updatedAt", Instant.now().toString());
                result.add(svc);
            }
        }
        return result;
    }

    private List<Map<String, Object>> buildLogsFromDocker() {
        List<Map<String, Object>> logs = new ArrayList<>();
        for (Map<String, Object> c : dockerSocket.containers()) {
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("time",    Instant.now().toString());
            log.put("service", containerName(c));
            log.put("level",   "running".equals(str(c.getOrDefault("State","unknown"))) ? "INFO" : "WARN");
            log.put("message", str(c.getOrDefault("Status", str(c.getOrDefault("State","unknown")))));
            logs.add(log);
        }
        return logs;
    }

    private String containerName(Map c) {
        Object names = c.get("Names");
        if (names instanceof List && !((List) names).isEmpty()) {
            return String.valueOf(((List) names).get(0)).replaceFirst("^/", "")
                         .replaceFirst("^nebulaops-v\\d+-\\d+-[^-]+-", "")
                         .replaceFirst("-\\d+$", "");
        }
        String id = str(c.getOrDefault("Id", "container"));
        return id.substring(0, Math.min(12, id.length()));
    }

    private String portsOf(Map c) {
        Object portsObj = c.get("Ports");
        if (!(portsObj instanceof List)) return "-";
        StringBuilder sb = new StringBuilder();
        for (Object p : (List) portsObj) {
            if (p instanceof Map) {
                Object pub = ((Map) p).get("PublicPort");
                Object priv = ((Map) p).get("PrivatePort");
                if (pub != null) sb.append(pub).append(":").append(priv).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String deploymentYamlText(String name, String image, String ns) {
        return "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: " + name
             + "\n  namespace: " + ns + "\nspec:\n  replicas: 1\n  selector:\n    matchLabels:\n"
             + "      app: " + name + "\n  template:\n    spec:\n      containers:\n        - name: "
             + name + "\n          image: " + image + "\n";
    }

    private String serviceYamlText(String name, String ns, String ports) {
        return "apiVersion: v1\nkind: Service\nmetadata:\n  name: " + name
             + "\n  namespace: " + ns + "\nspec:\n  selector:\n    app: " + name
             + "\n  # ports: " + ports + "\n";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SHARED HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private Map<String, Object> run(String cmd, String action, String target, int timeout) {
        ToolResult r = tools.shell(cmd, timeout);
        return Map.of("ok", r.ok(), "action", action, "target", target,
                      "stdout", r.stdout(), "stderr", r.stderr(),
                      "exitCode", r.exitCode(), "durationMs", r.durationMs());
    }

    private Map<String, Object> applyYaml(String yaml, String action, String target) {
        if (yaml == null || yaml.isBlank())
            return Map.of("ok", false, "error", "yaml required", "action", action, "target", target);
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("nebula-", ".yaml");
            java.nio.file.Files.writeString(tmp, yaml);
            String verb = action.contains("delete") ? "delete" : "apply";
            ToolResult r = tools.shell("kubectl " + verb + " -f " + tmp.toAbsolutePath(), 30);
            java.nio.file.Files.deleteIfExists(tmp);
            return Map.of("ok", r.ok(), "action", action, "target", target,
                          "stdout", r.stdout(), "stderr", r.stderr(), "exitCode", r.exitCode());
        } catch (Exception e) {
            return Map.of("ok", false, "action", action, "target", target, "error", e.getMessage());
        }
    }

    private Map asMap(Object o) { return o instanceof Map ? (Map) o : Collections.emptyMap(); }
    private String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private String safe(String s) {
        if (s == null) return "";
        return s.replaceAll("[^A-Za-z0-9._:\\-]", "");
    }
}
