package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.client.DockerSocketClient;
import dev.nebulaops.gateway.client.ToolCommandClient;
import dev.nebulaops.gateway.client.ToolResult;
import dev.nebulaops.gateway.service.KubernetesPlatformService;
import dev.nebulaops.gateway.kubernetes.KubeConfigRecord;
import dev.nebulaops.gateway.kubernetes.KubeConfigRegistryService;
import dev.nebulaops.gateway.service.PlatformEventPublisher;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.*;

/**
 * v23.2 — Kubernetes controller: live snapshot + all OpenLens actions.
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
    private final PlatformEventPublisher   events;
    private final KubeConfigRegistryService kubeConfigRegistry;

    public KubernetesOpsController(KubernetesPlatformService service,
                                   DockerSocketClient dockerSocket,
                                   ToolCommandClient tools,
                                   PlatformEventPublisher events,
                                   KubeConfigRegistryService kubeConfigRegistry) {
        this.service      = service;
        this.dockerSocket = dockerSocket;
        this.tools        = tools;
        this.events       = events;
        this.kubeConfigRegistry = kubeConfigRegistry;
    }

    @GetMapping("/kubeconfigs")
    public Map<String, Object> kubeconfigs() {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            out.put("live", true);
            out.put("currentContext", service.currentContextSummary());
            out.put("items", kubeConfigRegistry.listSummaries());
            out.put("source", "mongodb:kubernetes_kubeconfigs");
        } catch (Exception e) {
            out.put("live", false);
            out.put("items", List.of());
            out.put("currentContext", service.currentContextSummary());
            out.put("error", "KUBECONFIG_REGISTRY_UNAVAILABLE: " + e.getMessage());
            out.put("source", "mongodb:kubernetes_kubeconfigs");
        }
        return out;
    }

    @PostMapping("/kubeconfigs")
    public Map<String, Object> saveKubeconfig(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            KubeConfigRecord saved = kubeConfigRegistry.save(body == null ? Collections.emptyMap() : body);
            out.put("ok", true);
            out.put("live", true);
            out.put("item", kubeConfigRegistry.summary(saved));
            out.put("source", "mongodb:kubernetes_kubeconfigs");
            out.put("correlationId", events.mutation("KUBECONFIG_SAVED", saved.getName(), true, out));
        } catch (Exception e) {
            out.put("ok", false);
            out.put("live", false);
            out.put("error", e.getMessage());
            out.put("correlationId", events.mutation("KUBECONFIG_SAVE_FAILED", "kubeconfig", false, out));
        }
        return out;
    }

    @DeleteMapping("/kubeconfigs/{id}")
    public Map<String, Object> deleteKubeconfig(@PathVariable String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            kubeConfigRegistry.delete(id);
            out.put("ok", true);
            out.put("live", true);
            out.put("id", id);
            out.put("correlationId", events.mutation("KUBECONFIG_DELETED", id, true, out));
        } catch (Exception e) {
            out.put("ok", false);
            out.put("live", false);
            out.put("id", id);
            out.put("error", e.getMessage());
            out.put("correlationId", events.mutation("KUBECONFIG_DELETE_FAILED", id, false, out));
        }
        return out;
    }

    @PostMapping("/kubeconfigs/{id}/probe")
    public Map<String, Object> probeKubeconfig(@PathVariable String id) {
        return service.cluster(id);
    }

    @GetMapping("/events")
    public Map<String, Object> eventsV23(@RequestParam(defaultValue = "all") String namespace,
                                         @RequestParam(required = false) String clusterId) {
        return service.events(namespace, clusterId);
    }

    @GetMapping("/namespaces/{ns}/graph")
    public Map<String, Object> namespaceGraphV23(@PathVariable String ns,
                                                 @RequestParam(required = false) String clusterId) {
        Map<String, Object> pods = service.resource("pods", ns, clusterId);
        Map<String, Object> deployments = service.resource("deployments", ns, clusterId);
        Map<String, Object> services = service.resource("services", ns, clusterId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("namespace", ns);
        out.put("live", Boolean.TRUE.equals(pods.get("live")) || Boolean.TRUE.equals(deployments.get("live")) || Boolean.TRUE.equals(services.get("live")));
        out.put("pods", pods);
        out.put("deployments", deployments);
        out.put("services", services);
        out.put("toolStatus", Map.of("source", "kubectl", "note", "Graph is assembled from live kubectl resources only."));
        return out;
    }

    @PostMapping("/resources/diff")
    public Map<String, Object> resourceDiffV23(@RequestBody(required = false) Map<String,Object> body,
                                               @RequestParam(required = false) String clusterId) {
        String yaml = body == null ? null : String.valueOf(body.getOrDefault("yaml", ""));
        if (yaml == null || yaml.isBlank()) return Map.of("ok", false, "live", false, "error", "yaml required");
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("nebula-diff-", ".yaml");
            java.nio.file.Files.writeString(tmp, yaml);
            ToolResult r = service.runKubectl("kubectl diff -f " + tmp.toAbsolutePath(), clusterId, 30);
            java.nio.file.Files.deleteIfExists(tmp);
            return Map.of("ok", r.ok(), "live", true, "stdout", r.stdout(), "stderr", r.stderr(), "exitCode", r.exitCode(), "toolStatus", r.asMap());
        } catch (Exception e) {
            return Map.of("ok", false, "live", false, "error", e.getMessage());
        }
    }

    @PostMapping("/resources/apply")
    public Map<String, Object> resourceApplyV23(@RequestBody(required = false) Map<String,Object> body,
                                                @RequestParam(required = false) String clusterId) {
        String yaml = body == null ? null : String.valueOf(body.getOrDefault("yaml", ""));
        return applyYaml(yaml, "apply-resource", "manifest", clusterId);
    }

    @GetMapping("/pods/{ns}/{name}/logs/stream")
    public Map<String, Object> podLogsStreamHintV23(@PathVariable String ns, @PathVariable String name,
                                                    @RequestParam(required = false) String clusterId) {
        return run("kubectl logs " + safe(name) + " -n " + safe(ns) + " --tail=100", "pod-logs", ns + "/" + name, 15, clusterId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // READ / SNAPSHOT ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/cluster")
    public Map<String, Object> clusterV23(@RequestParam(required = false) String clusterId) {
        return service.cluster(clusterId);
    }

    @GetMapping("/snapshot")
    public Map<String, Object> snapshot(@RequestParam(required = false) String clusterId) {
        Map<String, Object> nodes = service.nodes(clusterId);
        Map<String, Object> pods = service.resource("pods", "all", clusterId);
        Map<String, Object> deployments = service.resource("deployments", "all", clusterId);
        Map<String, Object> services = service.resource("services", "all", clusterId);
        Map<String, Object> eventsRaw = service.events("all", clusterId);
        boolean live = Boolean.TRUE.equals(nodes.get("live")) || Boolean.TRUE.equals(pods.get("live"));
        Map<String, Object> cluster = new LinkedHashMap<>();
        cluster.put("name", live ? "kubectl-current-context" : "unavailable");
        cluster.put("provider", "kubectl");
        cluster.put("status", live ? "Connected" : "Unavailable");
        cluster.put("live", live);
        cluster.put("generatedAt", Instant.now().toString());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cluster", cluster); out.put("nodes", nodes); out.put("pods", pods); out.put("deployments", deployments); out.put("services", services); out.put("events", eventsRaw);
        out.put("resources", mergeKubernetesItems(pods, deployments, services, nodes));
        out.put("logs", kubernetesLogsFromEvents(eventsRaw));
        out.put("live", live);
        out.put("toolStatus", live ? Map.of("source", "kubectl") : nodes.get("toolStatus"));
        return out;
    }

    @GetMapping("/logs")
    public List<Map<String, Object>> logs(@RequestParam(required = false) String namespace,
                                          @RequestParam(required = false) String clusterId) {
        Map raw = service.events(namespace, clusterId);
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
        return result;
    }

    @GetMapping("/health")
    public Map<String, Object> health(@RequestParam(required = false) String clusterId) { return service.nodes(clusterId); }

    @GetMapping("/nodes")
    public Map<String, Object> nodes(@RequestParam(required = false) String clusterId) { return service.nodes(clusterId); }

    @GetMapping("/namespaces")
    public Map<String, Object> namespaces(@RequestParam(required = false) String clusterId) { return service.namespaces(clusterId); }

    @GetMapping("/helm/releases")
    public Map<String, Object> helmReleases(@RequestParam(defaultValue = "all") String namespace,
                                            @RequestParam(required = false) String clusterId) {
        return service.helmReleases(namespace, clusterId);
    }

    @GetMapping("/resources")
    public Map<String, Object> list(@RequestParam(defaultValue = "pods") String kind,
                                    @RequestParam(required = false) String namespace,
                                    @RequestParam(required = false) String clusterId) {
        return service.resource(kind, namespace, clusterId);
    }

    @GetMapping("/resources/{resourceId}")
    public Map<String, Object> getResource(@PathVariable String resourceId,
                                           @RequestParam(required = false) String clusterId) {
        return service.resource(resourceId, null, clusterId);
    }

    /** Renamed from /{kind} to /kind/{kind} to avoid ambiguity with action paths */
    @GetMapping("/kind/{kind}")
    public Map<String, Object> resource(@PathVariable String kind,
                                        @RequestParam(required = false) String namespace,
                                        @RequestParam(required = false) String clusterId) {
        return service.resource(kind, namespace, clusterId);
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
        String clusterId = requestClusterId();
        ToolResult r = service.runKubectl(
            "kubectl get pod " + safe(name) + " -n " + safe(ns)
            + " -o jsonpath='{.metadata.ownerReferences[0].kind}/{.metadata.ownerReferences[0].name}'", clusterId, 10);
        if (r.ok() && !r.stdout().isBlank()) {
            String owner = r.stdout().trim().replace("'", "");
            if (owner.startsWith("ReplicaSet/")) {
                String rsName = owner.substring("ReplicaSet/".length());
                ToolResult rd = service.runKubectl(
                    "kubectl get replicaset " + rsName + " -n " + safe(ns)
                    + " -o jsonpath='{.metadata.ownerReferences[0].name}'", clusterId, 8);
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

    @GetMapping("/statefulsets/{ns}/{name}/yaml")
    public Map<String, Object> statefulSetYaml(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl get statefulset " + safe(name) + " -n " + safe(ns) + " -o yaml", "get-yaml", ns + "/" + name, 10);
    }

    @PostMapping("/statefulsets/{ns}/{name}/yaml")
    public Map<String, Object> applyStatefulSetYaml(@PathVariable String ns, @PathVariable String name,
            @RequestBody Map<String, Object> body) {
        return applyYaml((String) body.get("yaml"), "apply-statefulset", ns + "/" + name);
    }

    @GetMapping("/statefulsets/{ns}/{name}/describe")
    public Map<String, Object> describeStatefulSet(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl describe statefulset " + safe(name) + " -n " + safe(ns), "describe-statefulset", ns + "/" + name, 15);
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

    @GetMapping("/configmaps/{ns}/{name}/describe")
    public Map<String, Object> describeConfigMap(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl describe configmap " + safe(name) + " -n " + safe(ns), "describe-configmap", ns + "/" + name, 15);
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
    // LIVE DATA HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private List<Map<String, Object>> mergeKubernetesItems(Map<String, Object>... sources) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> source : sources) {
            Object data = source.get("data");
            if (data instanceof Map) {
                Object items = ((Map) data).get("items");
                if (items instanceof List) {
                    for (Object item : (List) items) if (item instanceof Map) result.add((Map<String, Object>) item);
                }
            }
        }
        return result;
    }

    private List<Map<String, Object>> kubernetesLogsFromEvents(Map<String, Object> eventsRaw) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object data = eventsRaw.get("data");
        if (data instanceof Map) {
            Object items = ((Map) data).get("items");
            if (items instanceof List) {
                for (Object ev : (List) items) {
                    if (!(ev instanceof Map)) continue;
                    Map event = (Map) ev;
                    Map involv = asMap(event.get("involvedObject"));
                    Map<String, Object> log = new LinkedHashMap<>();
                    log.put("time", str(event.getOrDefault("lastTimestamp", event.getOrDefault("eventTime", Instant.now().toString()))));
                    log.put("service", str(involv.getOrDefault("name", "kubernetes")));
                    log.put("level", "Warning".equals(event.get("type")) ? "WARN" : "INFO");
                    log.put("message", str(event.getOrDefault("message", "")));
                    result.add(log);
                }
            }
        }
        return result;
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
        return run(cmd, action, target, timeout, requestClusterId());
    }

    private Map<String, Object> run(String cmd, String action, String target, int timeout, String clusterId) {
        ToolResult r = service.runKubectl(cmd, clusterId, timeout);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", r.ok());
        payload.put("action", action);
        payload.put("target", target);
        payload.put("stdout", r.stdout());
        payload.put("stderr", r.stderr());
        payload.put("exitCode", r.exitCode());
        payload.put("durationMs", r.durationMs());
        payload.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
        String correlationId = events.mutation("KUBERNETES_" + action.toUpperCase(Locale.ROOT).replace('-', '_'), target, r.ok(), payload);
        payload.put("correlationId", correlationId);
        return payload;
    }

    private Map<String, Object> applyYaml(String yaml, String action, String target) {
        return applyYaml(yaml, action, target, requestClusterId());
    }

    private Map<String, Object> applyYaml(String yaml, String action, String target, String clusterId) {
        if (yaml == null || yaml.isBlank()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", false);
            payload.put("error", "yaml required");
            payload.put("action", action);
            payload.put("target", target);
            payload.put("correlationId", events.mutation("KUBERNETES_" + action.toUpperCase(Locale.ROOT).replace('-', '_'), target, false, payload));
            return payload;
        }
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("nebula-", ".yaml");
            java.nio.file.Files.writeString(tmp, yaml);
            String verb = action.contains("delete") ? "delete" : "apply";
            ToolResult r = service.runKubectl("kubectl " + verb + " -f " + tmp.toAbsolutePath(), clusterId, 30);
            java.nio.file.Files.deleteIfExists(tmp);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", r.ok());
            payload.put("action", action);
            payload.put("target", target);
            payload.put("stdout", r.stdout());
            payload.put("stderr", r.stderr());
            payload.put("exitCode", r.exitCode());
            payload.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
            payload.put("correlationId", events.mutation("KUBERNETES_" + action.toUpperCase(Locale.ROOT).replace('-', '_'), target, r.ok(), payload));
            return payload;
        } catch (Exception e) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", false);
            payload.put("action", action);
            payload.put("target", target);
            payload.put("error", e.getMessage());
            payload.put("correlationId", events.mutation("KUBERNETES_" + action.toUpperCase(Locale.ROOT).replace('-', '_'), target, false, payload));
            return payload;
        }
    }

    private String requestClusterId() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                String value = attrs.getRequest().getParameter("clusterId");
                return value == null ? null : value.trim();
            }
        } catch (Exception ignored) { }
        return null;
    }

    private Map asMap(Object o) { return o instanceof Map ? (Map) o : Collections.emptyMap(); }
    private String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private String safe(String s) {
        if (s == null) return "";
        return s.replaceAll("[^A-Za-z0-9._:\\-]", "");
    }
}
