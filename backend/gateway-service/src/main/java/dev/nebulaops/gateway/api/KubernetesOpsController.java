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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * v24.1 — Kubernetes controller: live snapshot + all OpenLens actions.
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

    @GetMapping("/helm/releases/{namespace}/{name}/status")
    public Map<String, Object> helmStatus(@PathVariable String namespace, @PathVariable String name,
                                          @RequestParam(required = false) String clusterId) {
        return helmRun("helm status " + safe(name) + " -n " + safe(namespace) + " -o json", "helm-status", namespace + "/" + name, 20, clusterId);
    }

    @GetMapping("/helm/releases/{namespace}/{name}/values")
    public Map<String, Object> helmValues(@PathVariable String namespace, @PathVariable String name,
                                          @RequestParam(required = false) String clusterId) {
        return helmRun("helm get values " + safe(name) + " -n " + safe(namespace) + " -o json", "helm-values", namespace + "/" + name, 20, clusterId);
    }

    @GetMapping("/helm/releases/{namespace}/{name}/history")
    public Map<String, Object> helmHistory(@PathVariable String namespace, @PathVariable String name,
                                           @RequestParam(required = false) String clusterId) {
        return helmRun("helm history " + safe(name) + " -n " + safe(namespace) + " -o json", "helm-history", namespace + "/" + name, 20, clusterId);
    }

    @PostMapping("/helm/releases/{namespace}/{name}/rollback")
    public Map<String, Object> helmRollback(@PathVariable String namespace, @PathVariable String name,
                                            @RequestBody(required = false) Map<String, Object> body,
                                            @RequestParam(required = false) String clusterId) {
        int revision = intValue(body == null ? 0 : body.getOrDefault("revision", 0));
        String suffix = revision > 0 ? " " + revision : "";
        return helmRun("helm rollback " + safe(name) + suffix + " -n " + safe(namespace), "helm-rollback", namespace + "/" + name, 60, clusterId);
    }

    @DeleteMapping("/helm/releases/{namespace}/{name}")
    public Map<String, Object> helmUninstall(@PathVariable String namespace, @PathVariable String name,
                                             @RequestParam(required = false) String clusterId) {
        return helmRun("helm uninstall " + safe(name) + " -n " + safe(namespace), "helm-uninstall", namespace + "/" + name, 60, clusterId);
    }

    @GetMapping("/rbac/summary")
    public Map<String, Object> rbacSummary(@RequestParam(defaultValue = "all") String namespace,
                                           @RequestParam(required = false) String clusterId) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean all = namespace == null || namespace.isBlank() || "all".equals(namespace);
        Map<String, Object> serviceAccounts = service.resource("serviceaccounts", all ? "all" : namespace, clusterId);
        Map<String, Object> roles = service.resource("roles", all ? "all" : namespace, clusterId);
        Map<String, Object> roleBindings = service.resource("rolebindings", all ? "all" : namespace, clusterId);
        Map<String, Object> clusterRoles = all ? service.resource("clusterroles", null, clusterId) : new LinkedHashMap<>(Map.of("live", true, "items", List.of(), "note", "ClusterRoles are available with namespace=all"));
        Map<String, Object> clusterRoleBindings = all ? service.resource("clusterrolebindings", null, clusterId) : new LinkedHashMap<>(Map.of("live", true, "items", List.of(), "note", "ClusterRoleBindings are available with namespace=all"));
        boolean live = Boolean.TRUE.equals(serviceAccounts.get("live"))
            || Boolean.TRUE.equals(roles.get("live"))
            || Boolean.TRUE.equals(roleBindings.get("live"))
            || Boolean.TRUE.equals(clusterRoles.get("live"))
            || Boolean.TRUE.equals(clusterRoleBindings.get("live"));
        out.put("live", live);
        out.put("tool", "kubectl");
        out.put("resource", "rbac-summary");
        out.put("namespace", all ? "all" : namespace);
        out.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
        out.put("serviceAccounts", serviceAccounts);
        out.put("roles", roles);
        out.put("roleBindings", roleBindings);
        out.put("clusterRoles", clusterRoles);
        out.put("clusterRoleBindings", clusterRoleBindings);
        out.put("items", List.of(
            Map.of("name", "ServiceAccounts", "status", Boolean.TRUE.equals(serviceAccounts.get("live")) ? "LIVE" : "UNAVAILABLE", "source", "kubectl get serviceaccounts"),
            Map.of("name", "Roles", "status", Boolean.TRUE.equals(roles.get("live")) ? "LIVE" : "UNAVAILABLE", "source", "kubectl get roles"),
            Map.of("name", "RoleBindings", "status", Boolean.TRUE.equals(roleBindings.get("live")) ? "LIVE" : "UNAVAILABLE", "source", "kubectl get rolebindings"),
            Map.of("name", "ClusterRoles", "status", all ? (Boolean.TRUE.equals(clusterRoles.get("live")) ? "LIVE" : "UNAVAILABLE") : "SKIPPED", "source", "kubectl get clusterroles"),
            Map.of("name", "ClusterRoleBindings", "status", all ? (Boolean.TRUE.equals(clusterRoleBindings.get("live")) ? "LIVE" : "UNAVAILABLE") : "SKIPPED", "source", "kubectl get clusterrolebindings")
        ));
        out.put("realDataOnly", true);
        return out;
    }

    @GetMapping("/rbac/can-i")
    public Map<String, Object> rbacCanI(@RequestParam String verb,
                                        @RequestParam String resource,
                                        @RequestParam(defaultValue = "default") String namespace,
                                        @RequestParam(required = false, name = "as") String asUser,
                                        @RequestParam(required = false) String clusterId) {
        String cmd = "kubectl auth can-i " + safe(verb) + " " + safeKindForCommand(resource) + " -n " + safe(namespace);
        if (asUser != null && !asUser.isBlank()) cmd += " --as=" + shellQuote(asUser.trim());
        ToolResult r = service.runKubectl(cmd, clusterId, 15);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", r.ok());
        out.put("live", r.ok());
        out.put("allowed", r.ok() && r.stdout().trim().equalsIgnoreCase("yes"));
        out.put("verb", verb);
        out.put("resource", resource);
        out.put("namespace", namespace);
        out.put("as", asUser == null || asUser.isBlank() ? "current-user" : asUser);
        out.put("stdout", r.stdout());
        out.put("stderr", r.stderr());
        out.put("exitCode", r.exitCode());
        out.put("toolStatus", r.asMap());
        out.put("realDataOnly", true);
        return out;
    }

    @GetMapping("/port-forwards")
    public Map<String, Object> portForwards(@RequestParam(required = false) String clusterId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", true);
        out.put("tool", "kubectl");
        out.put("resource", "port-forwards");
        out.put("clusterId", clusterId == null || clusterId.isBlank() ? "all" : clusterId);
        out.put("items", listPortForwards(clusterId));
        out.put("count", ((List<?>) out.get("items")).size());
        out.put("realDataOnly", true);
        return out;
    }

    @PostMapping("/port-forwards")
    public Map<String, Object> startPortForward(@RequestBody(required = false) Map<String, Object> body,
                                                @RequestParam(required = false) String clusterId) {
        String ns = safe(str(body == null ? "default" : body.getOrDefault("namespace", "default")));
        String kind = safeKindForCommand(str(body == null ? "service" : body.getOrDefault("kind", "service")));
        String name = safe(str(body == null ? "" : body.getOrDefault("name", "")));
        int localPort = intValue(body == null ? 0 : body.getOrDefault("localPort", 0));
        int remotePort = intValue(body == null ? 0 : body.getOrDefault("remotePort", 0));
        if (name.isBlank() || localPort <= 0 || remotePort <= 0) return Map.of("ok", false, "live", false, "error", "namespace, kind, name, localPort and remotePort are required");
        String id = safe(ns + "-" + kind + "-" + name + "-" + localPort + "-" + remotePort + "-" + str(clusterId == null ? "current" : clusterId));
        Path dir = portForwardDir();
        Path log = dir.resolve(id + ".log");
        Path meta = dir.resolve(id + ".properties");
        Path kubeconfig = null;
        try {
            Files.createDirectories(dir);
            String kubectl = "kubectl";
            if (clusterId != null && !clusterId.isBlank() && !"current-context".equals(clusterId) && !"current".equals(clusterId)) {
                kubeconfig = dir.resolve(id + ".kubeconfig.yaml");
                Path original = kubeConfigRegistry.writeTempKubeconfig(clusterId);
                try {
                    Files.copy(original, kubeconfig, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(original);
                }
                kubectl = "kubectl --kubeconfig " + shellQuote(kubeconfig.toAbsolutePath().toString());
            }
            String cmd = "nohup " + kubectl + " -n " + safe(ns) + " port-forward --address 127.0.0.1 "
                    + safe(kind) + "/" + safe(name) + " " + localPort + ":" + remotePort + " > " + shellQuote(log.toString()) + " 2>&1 & echo $!";
            ToolResult r = tools.shell(cmd, 8);
            Properties props = new Properties();
            props.setProperty("id", id);
            props.setProperty("pid", r.stdout().trim());
            props.setProperty("namespace", ns);
            props.setProperty("kind", kind);
            props.setProperty("name", name);
            props.setProperty("localPort", String.valueOf(localPort));
            props.setProperty("remotePort", String.valueOf(remotePort));
            props.setProperty("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
            props.setProperty("log", log.toString());
            if (kubeconfig != null) props.setProperty("kubeconfig", kubeconfig.toString());
            props.setProperty("createdAt", Instant.now().toString());
            try (java.io.OutputStream os = Files.newOutputStream(meta)) { props.store(os, "NebulaOps OpenLens port-forward"); }
            Map<String, Object> payload = portForwardRecord(meta, clusterId);
            payload.put("ok", r.ok() && !r.stdout().isBlank());
            payload.put("stdout", r.stdout());
            payload.put("stderr", r.stderr());
            payload.put("toolStatus", r.asMap());
            payload.put("correlationId", events.mutation("KUBERNETES_PORT_FORWARD_START", ns + "/" + name, Boolean.TRUE.equals(payload.get("ok")), payload));
            return payload;
        } catch (Exception e) {
            return Map.of("ok", false, "live", false, "action", "port-forward-start", "error", e.getMessage());
        }
    }

    @DeleteMapping("/port-forwards/{id}")
    public Map<String, Object> stopPortForward(@PathVariable String id,
                                               @RequestParam(required = false) String clusterId) {
        try {
            Path meta = portForwardDir().resolve(safe(id) + ".properties");
            Properties props = loadProps(meta);
            String pid = props.getProperty("pid", "");
            ToolResult r = tools.shell("kill " + safe(pid) + " >/dev/null 2>&1 || true", 5);
            Files.deleteIfExists(meta);
            String logPath = props.getProperty("log", "");
            if (!logPath.isBlank()) Files.deleteIfExists(Path.of(logPath));
            String kubeconfig = props.getProperty("kubeconfig", "");
            if (!kubeconfig.isBlank()) Files.deleteIfExists(Path.of(kubeconfig));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", true);
            payload.put("live", true);
            payload.put("action", "port-forward-stop");
            payload.put("id", id);
            payload.put("toolStatus", r.asMap());
            payload.put("correlationId", events.mutation("KUBERNETES_PORT_FORWARD_STOP", id, true, payload));
            return payload;
        } catch (Exception e) {
            return Map.of("ok", false, "live", false, "action", "port-forward-stop", "id", id, "error", e.getMessage());
        }
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

    @GetMapping("/problems")
    public Map<String, Object> problems(@RequestParam(defaultValue = "all") String namespace,
                                        @RequestParam(required = false) String clusterId) {
        Map<String, Object> pods = service.resource("pods", namespace, clusterId);
        Map<String, Object> eventsRaw = service.events(namespace, clusterId);
        Map<String, Object> nodes = service.nodes(clusterId);
        Map<String, Object> pvcs = service.resource("persistentvolumeclaims", namespace, clusterId);
        Map<String, Object> endpoints = service.resource("endpoints", namespace, clusterId);
        Map<String, Object> services = service.resource("services", namespace, clusterId);
        List<Map<String, Object>> items = detectProblems(namespace, pods, eventsRaw, nodes, pvcs, endpoints, services);
        Map<String, Object> out = new LinkedHashMap<>();
        boolean live = Boolean.TRUE.equals(pods.get("live")) || Boolean.TRUE.equals(eventsRaw.get("live")) || Boolean.TRUE.equals(nodes.get("live"));
        out.put("live", live);
        out.put("tool", "kubectl");
        out.put("resource", "problem-detector");
        out.put("namespace", namespace);
        out.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
        out.put("items", items);
        out.put("count", items.size());
        out.put("summary", problemSummary(items));
        out.put("sources", Map.of("pods", pods.get("toolStatus"), "events", eventsRaw.get("toolStatus"), "nodes", nodes.get("toolStatus")));
        out.put("realDataOnly", true);
        return out;
    }

    @GetMapping("/namespaces/{ns}/overview")
    public Map<String, Object> namespaceOverview(@PathVariable String ns,
                                                 @RequestParam(required = false) String clusterId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", true);
        out.put("namespace", ns);
        out.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
        out.put("pods", service.resource("pods", ns, clusterId));
        out.put("deployments", service.resource("deployments", ns, clusterId));
        out.put("statefulsets", service.resource("statefulsets", ns, clusterId));
        out.put("daemonsets", service.resource("daemonsets", ns, clusterId));
        out.put("jobs", service.resource("jobs", ns, clusterId));
        out.put("cronjobs", service.resource("cronjobs", ns, clusterId));
        out.put("services", service.resource("services", ns, clusterId));
        out.put("ingresses", service.resource("ingress", ns, clusterId));
        out.put("configmaps", service.resource("configmaps", ns, clusterId));
        out.put("secrets", maskedSecrets(ns, clusterId));
        out.put("pvcs", service.resource("persistentvolumeclaims", ns, clusterId));
        out.put("events", service.events(ns, clusterId));
        out.put("realDataOnly", true);
        return out;
    }

    @GetMapping("/workloads/{kind}/{ns}/{name}/detail")
    public Map<String, Object> workloadDetail(@PathVariable String kind, @PathVariable String ns, @PathVariable String name,
                                              @RequestParam(required = false) String clusterId) {
        String k = safeKindForCommand(kind);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", k);
        out.put("namespace", ns);
        out.put("name", name);
        out.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
        out.put("json", kubectlJson("kubectl get " + k + " " + safe(name) + " -n " + safe(ns) + " -o json", clusterId, 12));
        out.put("yaml", run("kubectl get " + k + " " + safe(name) + " -n " + safe(ns) + " -o yaml", "workload-yaml", ns + "/" + name, 12, clusterId));
        out.put("describe", run("kubectl describe " + k + " " + safe(name) + " -n " + safe(ns), "workload-describe", ns + "/" + name, 15, clusterId));
        out.put("events", run("kubectl get events -n " + safe(ns) + " --field-selector involvedObject.name=" + safe(name) + " -o json", "workload-events", ns + "/" + name, 12, clusterId));
        out.put("live", Boolean.TRUE);
        out.put("realDataOnly", true);
        return out;
    }

    @PostMapping("/resources/dry-run")
    public Map<String, Object> dryRunApply(@RequestBody(required = false) Map<String,Object> body,
                                           @RequestParam(required = false) String clusterId) {
        String yaml = body == null ? null : String.valueOf(body.getOrDefault("yaml", ""));
        if (yaml == null || yaml.isBlank()) return Map.of("ok", false, "live", false, "error", "yaml required");
        try {
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("nebula-dry-run-", ".yaml");
            java.nio.file.Files.writeString(tmp, yaml);
            ToolResult r = service.runKubectl("kubectl apply --dry-run=server -f " + tmp.toAbsolutePath(), clusterId, 30);
            java.nio.file.Files.deleteIfExists(tmp);
            return Map.of("ok", r.ok(), "live", true, "action", "server-dry-run", "stdout", r.stdout(), "stderr", r.stderr(), "exitCode", r.exitCode(), "toolStatus", r.asMap());
        } catch (Exception e) {
            return Map.of("ok", false, "live", false, "action", "server-dry-run", "error", e.getMessage());
        }
    }


    @GetMapping("/storage/summary")
    public Map<String, Object> storageSummary(@RequestParam(defaultValue = "all") String namespace,
                                              @RequestParam(required = false) String clusterId) {
        Map<String, Object> pvcs = service.resource("persistentvolumeclaims", namespace, clusterId);
        Map<String, Object> pvs = service.resource("persistentvolumes", null, clusterId);
        Map<String, Object> storageClasses = service.resource("storageclasses", null, clusterId);
        Map<String, Object> quotas = service.resource("resourcequotas", namespace, clusterId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", Boolean.TRUE.equals(pvcs.get("live")) || Boolean.TRUE.equals(pvs.get("live")) || Boolean.TRUE.equals(storageClasses.get("live")));
        out.put("tool", "kubectl");
        out.put("resource", "storage-summary");
        out.put("namespace", namespace);
        out.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
        out.put("pvcs", pvcs);
        out.put("pvs", pvs);
        out.put("storageClasses", storageClasses);
        out.put("resourceQuotas", quotas);
        out.put("items", storageSummaryItems(pvcs, pvs, storageClasses, quotas));
        out.put("summary", Map.of(
            "pvcs", itemsFromPayload(pvcs).size(),
            "pvs", itemsFromPayload(pvs).size(),
            "storageClasses", itemsFromPayload(storageClasses).size(),
            "quotas", itemsFromPayload(quotas).size()
        ));
        out.put("realDataOnly", true);
        return out;
    }

    @GetMapping("/network/summary")
    public Map<String, Object> networkSummary(@RequestParam(defaultValue = "all") String namespace,
                                              @RequestParam(required = false) String clusterId) {
        Map<String, Object> services = service.resource("services", namespace, clusterId);
        Map<String, Object> endpoints = service.resource("endpoints", namespace, clusterId);
        Map<String, Object> ingress = service.resource("ingress", namespace, clusterId);
        Map<String, Object> networkPolicies = service.resource("networkpolicies", namespace, clusterId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", Boolean.TRUE.equals(services.get("live")) || Boolean.TRUE.equals(endpoints.get("live")) || Boolean.TRUE.equals(ingress.get("live")));
        out.put("tool", "kubectl");
        out.put("resource", "network-summary");
        out.put("namespace", namespace);
        out.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
        out.put("services", services);
        out.put("endpoints", endpoints);
        out.put("ingress", ingress);
        out.put("networkPolicies", networkPolicies);
        out.put("items", networkSummaryItems(services, endpoints, ingress, networkPolicies));
        out.put("summary", Map.of(
            "services", itemsFromPayload(services).size(),
            "endpoints", itemsFromPayload(endpoints).size(),
            "ingress", itemsFromPayload(ingress).size(),
            "networkPolicies", itemsFromPayload(networkPolicies).size()
        ));
        out.put("realDataOnly", true);
        return out;
    }



    @GetMapping("/network/graph")
    public Map<String, Object> networkGraph(@RequestParam(defaultValue = "all") String namespace,
                                            @RequestParam(required = false) String clusterId) {
        Map<String, Object> services = service.resource("services", namespace, clusterId);
        Map<String, Object> endpoints = service.resource("endpoints", namespace, clusterId);
        Map<String, Object> ingress = service.resource("ingress", namespace, clusterId);
        Map<String, Object> pods = service.resource("pods", namespace, clusterId);
        Map<String, Object> networkPolicies = service.resource("networkpolicies", namespace, clusterId);

        List<Map<String, Object>> svcItems = itemsFromPayload(services);
        List<Map<String, Object>> endpointItems = itemsFromPayload(endpoints);
        List<Map<String, Object>> ingressItems = itemsFromPayload(ingress);
        List<Map<String, Object>> podItems = itemsFromPayload(pods);
        List<Map<String, Object>> policyItems = itemsFromPayload(networkPolicies);

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        List<Map<String, Object>> findings = new ArrayList<>();
        Set<String> namespaces = new TreeSet<>();

        for (Map<String, Object> pod : podItems) {
            Map meta = asMap(pod.get("metadata"));
            Map spec = asMap(pod.get("spec"));
            Map status = asMap(pod.get("status"));
            String podNs = str(meta.getOrDefault("namespace", "default"));
            String podName = str(meta.get("name"));
            if (podName.isBlank()) continue;
            namespaces.add(podNs);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("phase", str(status.get("phase")));
            details.put("podIp", str(status.get("podIP")));
            details.put("nodeName", str(spec.get("nodeName")));
            details.put("labels", asMap(meta.get("labels")));
            details.put("ownerReferences", meta.get("ownerReferences"));
            Map<String, Object> node = kubeNetworkNode("pod", kubeNetworkId("pod", podNs, podName), podName, podNs, details);
            String phase = str(status.get("phase"));
            node.put("severity", "Running".equals(phase) || "Succeeded".equals(phase) ? "OK" : phase.isBlank() ? "WARN" : severityForPhase(phase));
            nodes.add(node);
            if (List.of("Pending", "Failed", "Unknown").contains(phase)) {
                findings.add(kubeFinding(severityForPhase(phase), "POD_" + phase.toUpperCase(Locale.ROOT), podNs + "/" + podName, "Pod phase is " + phase + "; network routes to this pod may fail.", List.of()));
            }
        }

        for (Map<String, Object> svc : svcItems) {
            Map meta = asMap(svc.get("metadata"));
            Map spec = asMap(svc.get("spec"));
            String svcNs = str(meta.getOrDefault("namespace", "default"));
            String svcName = str(meta.get("name"));
            if (svcName.isBlank()) continue;
            namespaces.add(svcNs);
            Map<String, Object> ep = findByName(endpointItems, svcNs, svcName);
            int readyEndpoints = endpointAddressCount(ep.get("subsets"), "addresses");
            int notReadyEndpoints = endpointAddressCount(ep.get("subsets"), "notReadyAddresses");
            List<Map<String, Object>> matchedPods = matchingPodsForServiceInNamespace(svc, podItems);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("type", str(spec.getOrDefault("type", "ClusterIP")));
            details.put("clusterIp", str(spec.get("clusterIP")));
            details.put("externalName", str(spec.get("externalName")));
            details.put("externalIPs", spec.get("externalIPs"));
            details.put("loadBalancer", asMap(asMap(svc.get("status")).get("loadBalancer")));
            details.put("selector", asMap(spec.get("selector")));
            details.put("ports", spec.get("ports"));
            details.put("portSummary", servicePortSummary(spec));
            details.put("readyEndpoints", readyEndpoints);
            details.put("notReadyEndpoints", notReadyEndpoints);
            details.put("selectedPods", matchedPods.size());
            Map<String, Object> serviceNode = kubeNetworkNode("service", kubeNetworkId("service", svcNs, svcName), svcName, svcNs, details);
            boolean externalName = "ExternalName".equals(str(spec.get("type")));
            if (readyEndpoints > 0 || externalName) serviceNode.put("severity", "OK");
            else serviceNode.put("severity", matchedPods.isEmpty() ? "ERROR" : "WARN");
            nodes.add(serviceNode);

            if (matchedPods.isEmpty() && !externalName) {
                findings.add(kubeFinding("ERROR", "SERVICE_SELECTOR_MATCHES_NO_PODS", svcNs + "/" + svcName, "Service selector does not match any live pod in the namespace.", List.of()));
            }
            if (readyEndpoints == 0 && !externalName) {
                findings.add(kubeFinding("ERROR", "SERVICE_WITHOUT_READY_ENDPOINTS", svcNs + "/" + svcName, "Service has no ready endpoints; traffic routed to it will not reach a ready backend.", List.of()));
            }
            if (notReadyEndpoints > 0) {
                findings.add(kubeFinding("WARN", "SERVICE_HAS_NOT_READY_ENDPOINTS", svcNs + "/" + svcName, "Service has " + notReadyEndpoints + " not-ready endpoint addresses.", List.of()));
            }

            for (Map<String, Object> pod : matchedPods) {
                Map podMeta = asMap(pod.get("metadata"));
                Map podStatus = asMap(pod.get("status"));
                String podName = str(podMeta.get("name"));
                String phase = str(podStatus.get("phase"));
                Map<String, Object> edge = kubeNetworkEdge(kubeNetworkId("service", svcNs, svcName), kubeNetworkId("pod", svcNs, podName), "selects", "Running".equals(phase) || "Succeeded".equals(phase) ? "OK" : "WARN");
                edge.put("ports", servicePortSummary(spec));
                edges.add(edge);
            }

            if (!ep.isEmpty() || readyEndpoints > 0 || notReadyEndpoints > 0) {
                String endpointId = kubeNetworkId("endpoint", svcNs, svcName);
                Map<String, Object> endpointDetails = new LinkedHashMap<>();
                endpointDetails.put("readyAddresses", readyEndpoints);
                endpointDetails.put("notReadyAddresses", notReadyEndpoints);
                endpointDetails.put("targetPods", endpointPodTargetNames(ep.get("subsets")));
                Map<String, Object> endpointNode = kubeNetworkNode("endpoint", endpointId, svcName + " endpoints", svcNs, endpointDetails);
                endpointNode.put("severity", readyEndpoints > 0 && notReadyEndpoints == 0 ? "OK" : readyEndpoints > 0 ? "WARN" : "ERROR");
                nodes.add(endpointNode);
                Map<String, Object> edge = kubeNetworkEdge(kubeNetworkId("service", svcNs, svcName), endpointId, "publishes", notReadyEndpoints > 0 || readyEndpoints == 0 ? "WARN" : "OK");
                edge.put("readyEndpoints", readyEndpoints);
                edge.put("notReadyEndpoints", notReadyEndpoints);
                edges.add(edge);
                for (String podName : endpointPodTargetNames(ep.get("subsets"))) {
                    edges.add(kubeNetworkEdge(endpointId, kubeNetworkId("pod", svcNs, podName), "targets", "OK"));
                }
            }
        }

        for (Map<String, Object> ing : ingressItems) {
            Map meta = asMap(ing.get("metadata"));
            Map spec = asMap(ing.get("spec"));
            String ingNs = str(meta.getOrDefault("namespace", "default"));
            String ingName = str(meta.get("name"));
            if (ingName.isBlank()) continue;
            namespaces.add(ingNs);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("hosts", ingressHosts(ing));
            details.put("tls", spec.get("tls"));
            details.put("className", str(spec.get("ingressClassName")));
            details.put("loadBalancer", asMap(asMap(ing.get("status")).get("loadBalancer")));
            Map<String, Object> ingressNode = kubeNetworkNode("ingress", kubeNetworkId("ingress", ingNs, ingName), ingName, ingNs, details);
            ingressNode.put("severity", ingressHosts(ing).isEmpty() ? "WARN" : "OK");
            nodes.add(ingressNode);
            Object tls = spec.get("tls");
            if (!(tls instanceof List<?> tlsRows) || tlsRows.isEmpty()) {
                findings.add(kubeFinding("WARN", "INGRESS_TLS_NOT_CONFIGURED", ingNs + "/" + ingName, "Ingress has no TLS section.", List.of()));
            }
            for (String serviceName : backendServiceNames(ing)) {
                Map<String, Object> svc = findByName(svcItems, ingNs, serviceName);
                Map<String, Object> ep = findByName(endpointItems, ingNs, serviceName);
                boolean missingService = svc.isEmpty();
                int readyEndpoints = endpointAddressCount(ep.get("subsets"), "addresses");
                String serviceId = kubeNetworkId("service", ingNs, serviceName);
                if (missingService) {
                    Map<String, Object> missing = kubeNetworkNode("missing-service", serviceId, serviceName, ingNs, Map.of("referenceOnly", true, "referencedBy", ingName));
                    missing.put("severity", "ERROR");
                    missing.put("status", "missing");
                    nodes.add(missing);
                    findings.add(kubeFinding("ERROR", "INGRESS_BACKEND_SERVICE_MISSING", ingNs + "/" + ingName, "Ingress backend service is missing: " + serviceName, List.of()));
                } else if (readyEndpoints == 0 && !"ExternalName".equals(str(asMap(svc.get("spec")).get("type")))) {
                    findings.add(kubeFinding("ERROR", "INGRESS_BACKEND_WITHOUT_ENDPOINTS", ingNs + "/" + ingName, "Ingress routes to a service with no ready endpoints: " + serviceName, List.of()));
                }
                Map<String, Object> edge = kubeNetworkEdge(kubeNetworkId("ingress", ingNs, ingName), serviceId, "routes-to", missingService || readyEndpoints == 0 ? "ERROR" : "OK");
                edge.put("readyEndpoints", readyEndpoints);
                edges.add(edge);
            }
        }

        for (Map<String, Object> policy : policyItems) {
            Map meta = asMap(policy.get("metadata"));
            Map spec = asMap(policy.get("spec"));
            String policyNs = str(meta.getOrDefault("namespace", "default"));
            String policyName = str(meta.get("name"));
            if (policyName.isBlank()) continue;
            namespaces.add(policyNs);
            List<Map<String, Object>> selectedPods = matchingPodsForNetworkPolicy(policy, podItems);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("policyTypes", spec.get("policyTypes"));
            details.put("podSelector", spec.get("podSelector"));
            details.put("ingressRuleCount", spec.get("ingress") instanceof List<?> ingressRules ? ingressRules.size() : 0);
            details.put("egressRuleCount", spec.get("egress") instanceof List<?> egressRules ? egressRules.size() : 0);
            details.put("selectedPods", selectedPods.size());
            Map<String, Object> policyNode = kubeNetworkNode("networkpolicy", kubeNetworkId("networkpolicy", policyNs, policyName), policyName, policyNs, details);
            policyNode.put("severity", selectedPods.isEmpty() ? "WARN" : "OK");
            nodes.add(policyNode);
            if (selectedPods.isEmpty()) findings.add(kubeFinding("WARN", "NETWORK_POLICY_SELECTS_NO_PODS", policyNs + "/" + policyName, "NetworkPolicy podSelector does not currently match any pod.", List.of()));
            for (Map<String, Object> pod : selectedPods) {
                String podName = str(asMap(pod.get("metadata")).get("name"));
                Map<String, Object> edge = kubeNetworkEdge(kubeNetworkId("networkpolicy", policyNs, policyName), kubeNetworkId("pod", policyNs, podName), "applies-to", "POLICY");
                edge.put("policyTypes", spec.get("policyTypes"));
                edges.add(edge);
            }
        }

        List<Map<String, Object>> dedupedNodes = dedupeKubeNodes(nodes);
        long errors = findings.stream().filter(f -> "ERROR".equals(f.get("severity"))).count();
        long warnings = findings.stream().filter(f -> "WARN".equals(f.get("severity"))).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", Boolean.TRUE.equals(services.get("live")) || Boolean.TRUE.equals(endpoints.get("live")) || Boolean.TRUE.equals(ingress.get("live")) || Boolean.TRUE.equals(pods.get("live")) || Boolean.TRUE.equals(networkPolicies.get("live")));
        out.put("tool", "kubectl");
        out.put("resource", "network-graph");
        out.put("namespace", namespace);
        out.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
        out.put("nodes", dedupedNodes);
        out.put("edges", edges);
        out.put("items", dedupedNodes);
        out.put("findings", findings);
        out.put("topologyHealth", errors > 0 ? "ERROR" : warnings > 0 ? "WARN" : "OK");
        out.put("summary", Map.of(
            "pods", podItems.size(),
            "services", svcItems.size(),
            "endpoints", endpointItems.size(),
            "ingress", ingressItems.size(),
            "networkPolicies", policyItems.size(),
            "namespaces", namespaces.size(),
            "nodes", dedupedNodes.size(),
            "edges", edges.size(),
            "errors", errors,
            "warnings", warnings
        ));
        out.put("namespaces", new ArrayList<>(namespaces));
        Map<String, Object> toolStatus = new LinkedHashMap<>();
        toolStatus.put("services", services.get("toolStatus"));
        toolStatus.put("endpoints", endpoints.get("toolStatus"));
        toolStatus.put("ingress", ingress.get("toolStatus"));
        toolStatus.put("pods", pods.get("toolStatus"));
        toolStatus.put("networkPolicies", networkPolicies.get("toolStatus"));
        out.put("toolStatus", toolStatus);
        out.put("realDataOnly", true);
        return out;
    }

    @GetMapping("/namespaces/{ns}/quotas")
    public Map<String, Object> namespaceQuotas(@PathVariable String ns,
                                               @RequestParam(required = false) String clusterId) {
        Map<String, Object> quotas = service.resource("resourcequotas", ns, clusterId);
        Map<String, Object> limits = service.resource("limitranges", ns, clusterId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", Boolean.TRUE.equals(quotas.get("live")) || Boolean.TRUE.equals(limits.get("live")));
        out.put("tool", "kubectl");
        out.put("resource", "namespace-quotas");
        out.put("namespace", ns);
        out.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
        out.put("resourceQuotas", quotas);
        out.put("limitRanges", limits);
        out.put("items", quotaSummaryItems(quotas, limits));
        out.put("count", quotaSummaryItems(quotas, limits).size());
        out.put("realDataOnly", true);
        return out;
    }

    @GetMapping("/workloads/{kind}/{ns}/{name}/rollout-status")
    public Map<String, Object> workloadRolloutStatus(@PathVariable String kind, @PathVariable String ns, @PathVariable String name,
                                                     @RequestParam(required = false) String clusterId) {
        String k = safeKindForCommand(kind);
        return run("kubectl rollout status " + k + "/" + safe(name) + " -n " + safe(ns) + " --timeout=10s", "rollout-status", ns + "/" + name, 15, clusterId);
    }

    @GetMapping("/pods/{ns}/{name}/diagnostics")
    public Map<String, Object> podDiagnostics(@PathVariable String ns, @PathVariable String name,
                                              @RequestParam(defaultValue = "150") int tail,
                                              @RequestParam(required = false) String container,
                                              @RequestParam(required = false) String clusterId) {
        String safeTail = String.valueOf(Math.max(1, Math.min(tail, 2000)));
        String containerArg = container == null || container.isBlank() ? "" : " -c " + safe(container);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", true);
        out.put("tool", "kubectl");
        out.put("resource", "pod-diagnostics");
        out.put("namespace", ns);
        out.put("name", name);
        out.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
        out.put("json", kubectlJson("kubectl get pod " + safe(name) + " -n " + safe(ns) + " -o json", clusterId, 12));
        out.put("describe", run("kubectl describe pod " + safe(name) + " -n " + safe(ns), "pod-describe", ns + "/" + name, 15, clusterId));
        out.put("logs", run("kubectl logs " + safe(name) + " -n " + safe(ns) + containerArg + " --tail=" + safeTail, "pod-logs", ns + "/" + name, 20, clusterId));
        out.put("previousLogs", run("kubectl logs " + safe(name) + " -n " + safe(ns) + containerArg + " --previous --tail=" + safeTail, "pod-previous-logs", ns + "/" + name, 20, clusterId));
        out.put("events", run("kubectl get events -n " + safe(ns) + " --field-selector involvedObject.name=" + safe(name) + " -o json", "pod-events", ns + "/" + name, 12, clusterId));
        out.put("realDataOnly", true);
        return out;
    }

    // ═══════════════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════════════
    // TROUBLESHOOTING AND ROOT-CAUSE ANALYSIS
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/pods/{ns}/{name}/root-cause")
    public Map<String, Object> podRootCause(@PathVariable String ns, @PathVariable String name,
                                            @RequestParam(required = false) String clusterId,
                                            @RequestParam(defaultValue = "200") int tail) {
        Map<String, Object> podJson = kubectlJson("kubectl get pod " + safe(name) + " -n " + safe(ns) + " -o json", clusterId, 12);
        Map<String, Object> describe = run("kubectl describe pod " + safe(name) + " -n " + safe(ns), "pod-describe", ns + "/" + name, 15, clusterId);
        Map<String, Object> logs = run("kubectl logs pod/" + safe(name) + " -n " + safe(ns) + " --all-containers=true --tail=" + Math.max(20, Math.min(tail, 2000)), "pod-logs", ns + "/" + name, 18, clusterId);
        Map<String, Object> previousLogs = run("kubectl logs pod/" + safe(name) + " -n " + safe(ns) + " --all-containers=true --previous --tail=" + Math.max(20, Math.min(tail, 2000)), "pod-previous-logs", ns + "/" + name, 18, clusterId);
        Map<String, Object> eventsRaw = run("kubectl get events -n " + safe(ns) + " --field-selector involvedObject.name=" + safe(name) + " -o json", "pod-events", ns + "/" + name, 12, clusterId);
        List<Map<String, Object>> findings = rootCauseFindings(ns + "/" + name, str(describe.get("stdout")), str(logs.get("stdout")), str(previousLogs.get("stdout")), str(eventsRaw.get("stdout")), str(podJson.get("stdout")), str(describe.get("stderr")), str(logs.get("stderr")), str(previousLogs.get("stderr")));
        Map<String, Object> out = troubleshootingPayload("pod-root-cause", ns, name, clusterId, findings);
        out.put("pod", podJson); out.put("describe", describe); out.put("logs", logs); out.put("previousLogs", previousLogs); out.put("events", eventsRaw);
        return out;
    }

    @GetMapping("/workloads/{kind}/{ns}/{name}/root-cause")
    public Map<String, Object> workloadRootCause(@PathVariable String kind, @PathVariable String ns, @PathVariable String name,
                                                 @RequestParam(required = false) String clusterId,
                                                 @RequestParam(defaultValue = "200") int tail) {
        String k = safeKindForCommand(kind);
        Map<String, Object> workload = kubectlJson("kubectl get " + k + " " + safe(name) + " -n " + safe(ns) + " -o json", clusterId, 12);
        Map<String, Object> rollout = run("kubectl rollout status " + k + "/" + safe(name) + " -n " + safe(ns) + " --timeout=10s", "workload-rollout-status", ns + "/" + name, 15, clusterId);
        Map<String, Object> describe = run("kubectl describe " + k + " " + safe(name) + " -n " + safe(ns), "workload-describe", ns + "/" + name, 15, clusterId);
        Map<String, Object> pods = service.resource("pods", ns, clusterId);
        Map<String, Object> eventsRaw = service.events(ns, clusterId);
        List<Map<String, Object>> findings = rootCauseFindings(ns + "/" + k + "/" + name, str(workload.get("stdout")), str(rollout.get("stdout")), str(rollout.get("stderr")), str(describe.get("stdout")), str(eventsRaw.get("data")));
        findings.addAll(workloadPodFindings(k, name, itemsFromPayload(pods)));
        Map<String, Object> out = troubleshootingPayload("workload-root-cause", ns, name, clusterId, findings);
        out.put("kind", k); out.put("workload", workload); out.put("rollout", rollout); out.put("describe", describe); out.put("pods", pods); out.put("events", eventsRaw);
        return out;
    }

    @GetMapping("/services/{ns}/{name}/connectivity")
    public Map<String, Object> serviceConnectivity(@PathVariable String ns, @PathVariable String name,
                                                   @RequestParam(required = false) String clusterId) {
        Map<String, Object> services = service.resource("services", ns, clusterId);
        Map<String, Object> endpoints = service.resource("endpoints", ns, clusterId);
        Map<String, Object> pods = service.resource("pods", ns, clusterId);
        Map<String, Object> ingresses = service.resource("ingress", ns, clusterId);
        Map<String, Object> networkPolicies = service.resource("networkpolicies", ns, clusterId);
        Map<String, Object> svc = findByName(itemsFromPayload(services), ns, name);
        Map<String, Object> ep = findByName(itemsFromPayload(endpoints), ns, name);
        List<Map<String, Object>> findings = serviceConnectivityFindings(ns, name, svc, ep, itemsFromPayload(pods), itemsFromPayload(ingresses), itemsFromPayload(networkPolicies));
        Map<String, Object> out = troubleshootingPayload("service-connectivity", ns, name, clusterId, findings);
        out.put("service", svc); out.put("endpoints", ep); out.put("pods", matchingPodsForService(svc, itemsFromPayload(pods))); out.put("ingresses", matchingIngressesForService(name, itemsFromPayload(ingresses))); out.put("networkPolicies", networkPolicies);
        return out;
    }

    @GetMapping("/ingress/{ns}/{name}/connectivity")
    public Map<String, Object> ingressConnectivity(@PathVariable String ns, @PathVariable String name,
                                                   @RequestParam(required = false) String clusterId) {
        Map<String, Object> ingresses = service.resource("ingress", ns, clusterId);
        Map<String, Object> services = service.resource("services", ns, clusterId);
        Map<String, Object> endpoints = service.resource("endpoints", ns, clusterId);
        Map<String, Object> ingress = findByName(itemsFromPayload(ingresses), ns, name);
        List<Map<String, Object>> findings = ingressConnectivityFindings(ns, name, ingress, itemsFromPayload(services), itemsFromPayload(endpoints));
        Map<String, Object> out = troubleshootingPayload("ingress-connectivity", ns, name, clusterId, findings);
        out.put("ingress", ingress); out.put("services", services); out.put("endpoints", endpoints);
        return out;
    }

    @GetMapping("/network/connectivity-summary")
    public Map<String, Object> connectivitySummary(@RequestParam(defaultValue = "all") String namespace,
                                                   @RequestParam(required = false) String clusterId) {
        Map<String, Object> services = service.resource("services", namespace, clusterId);
        Map<String, Object> endpoints = service.resource("endpoints", namespace, clusterId);
        Map<String, Object> pods = service.resource("pods", namespace, clusterId);
        Map<String, Object> ingress = service.resource("ingress", namespace, clusterId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> svc : itemsFromPayload(services)) {
            Map meta = asMap(svc.get("metadata"));
            String rowNs = str(meta.getOrDefault("namespace", "default"));
            String rowName = str(meta.get("name"));
            Map<String, Object> ep = findByName(itemsFromPayload(endpoints), rowNs, rowName);
            List<Map<String, Object>> findings = serviceConnectivityFindings(rowNs, rowName, svc, ep, itemsFromPayload(pods), itemsFromPayload(ingress), List.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rowNs + "/" + rowName); row.put("namespace", rowNs); row.put("name", rowName); row.put("kind", "Service"); row.put("findings", findings); row.put("status", findings.stream().anyMatch(f -> "ERROR".equals(f.get("severity"))) ? "ERROR" : findings.isEmpty() ? "OK" : "WARN");
            items.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", Boolean.TRUE.equals(services.get("live")) || Boolean.TRUE.equals(endpoints.get("live"))); out.put("tool", "kubectl"); out.put("resource", "connectivity-summary"); out.put("namespace", namespace); out.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
        out.put("items", items); out.put("count", items.size()); out.put("summary", Map.of("services", items.size(), "errors", items.stream().filter(r -> "ERROR".equals(r.get("status"))).count(), "warnings", items.stream().filter(r -> "WARN".equals(r.get("status"))).count())); out.put("realDataOnly", true);
        return out;
    }

    @GetMapping("/workloads/{kind}/{ns}/{name}/dependency-map")
    public Map<String, Object> workloadDependencyMap(@PathVariable String kind, @PathVariable String ns, @PathVariable String name,
                                                     @RequestParam(required = false) String clusterId) {
        return dependencyMapFor(ns, safeKindForCommand(kind), name, clusterId);
    }

    @GetMapping("/namespaces/{ns}/dependency-map")
    public Map<String, Object> namespaceDependencyMap(@PathVariable String ns, @RequestParam(required = false) String clusterId) {
        return dependencyMapFor(ns, "namespace", ns, clusterId);
    }

    // POD ACTIONS
    // ═══════════════════════════════════════════════════════════════════════


    @GetMapping("/security/summary")
    public Map<String, Object> securitySummary(@RequestParam(defaultValue = "all") String namespace,
                                               @RequestParam(required = false) String clusterId) {
        Map<String, Object> pods = service.resource("pods", namespace, clusterId);
        Map<String, Object> serviceAccounts = service.resource("serviceaccounts", namespace, clusterId);
        Map<String, Object> networkPolicies = service.resource("networkpolicies", namespace, clusterId);
        List<Map<String, Object>> items = kubernetesSecurityFindings(itemsFromPayload(pods));
        Map<String, Object> out = new LinkedHashMap<>();
        boolean live = Boolean.TRUE.equals(pods.get("live"));
        out.put("live", live);
        out.put("tool", "kubectl");
        out.put("resource", "security-summary");
        out.put("namespace", namespace);
        out.put("items", items);
        out.put("count", items.size());
        out.put("summary", Map.of(
            "findings", items.size(),
            "errors", items.stream().filter(r -> "ERROR".equals(r.get("severity"))).count(),
            "warnings", items.stream().filter(r -> "WARN".equals(r.get("severity"))).count(),
            "serviceAccounts", itemsFromPayload(serviceAccounts).size(),
            "networkPolicies", itemsFromPayload(networkPolicies).size()
        ));
        out.put("toolStatus", pods.get("toolStatus"));
        out.put("realDataOnly", true);
        return out;
    }

    @GetMapping("/network-policies")
    public Map<String, Object> networkPolicies(@RequestParam(defaultValue = "all") String namespace,
                                               @RequestParam(required = false) String clusterId) {
        Map<String, Object> policies = service.resource("networkpolicies", namespace, clusterId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", Boolean.TRUE.equals(policies.get("live")));
        out.put("tool", "kubectl");
        out.put("resource", "networkpolicies");
        out.put("namespace", namespace);
        out.put("items", itemsFromPayload(policies));
        out.put("count", itemsFromPayload(policies).size());
        out.put("toolStatus", policies.get("toolStatus"));
        out.put("realDataOnly", true);
        return out;
    }

    @GetMapping("/ingress/tls")
    public Map<String, Object> ingressTls(@RequestParam(defaultValue = "all") String namespace,
                                          @RequestParam(required = false) String clusterId) {
        Map<String, Object> ingresses = service.resource("ingress", namespace, clusterId);
        List<Map<String, Object>> items = ingressTlsFindings(itemsFromPayload(ingresses));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", Boolean.TRUE.equals(ingresses.get("live")));
        out.put("tool", "kubectl");
        out.put("resource", "ingress-tls");
        out.put("namespace", namespace);
        out.put("items", items);
        out.put("count", items.size());
        out.put("summary", Map.of("ingresses", items.size(), "withoutTls", items.stream().filter(r -> "WARN".equals(r.get("severity"))).count()));
        out.put("toolStatus", ingresses.get("toolStatus"));
        out.put("realDataOnly", true);
        return out;
    }

    @GetMapping("/service-endpoints")
    public Map<String, Object> serviceEndpoints(@RequestParam(defaultValue = "all") String namespace,
                                                @RequestParam(required = false) String clusterId) {
        Map<String, Object> services = service.resource("services", namespace, clusterId);
        Map<String, Object> endpoints = service.resource("endpoints", namespace, clusterId);
        List<Map<String, Object>> items = serviceEndpointRows(itemsFromPayload(services), itemsFromPayload(endpoints));
        Map<String, Object> out = new LinkedHashMap<>();
        boolean live = Boolean.TRUE.equals(services.get("live")) || Boolean.TRUE.equals(endpoints.get("live"));
        out.put("live", live);
        out.put("tool", "kubectl");
        out.put("resource", "service-endpoints");
        out.put("namespace", namespace);
        out.put("items", items);
        out.put("count", items.size());
        out.put("summary", Map.of("services", items.size(), "withoutReadyEndpoints", items.stream().filter(r -> "WARN".equals(r.get("status"))).count()));
        out.put("toolStatus", services.get("toolStatus"));
        out.put("realDataOnly", true);
        return out;
    }

    @GetMapping("/events/timeline")
    public Map<String, Object> eventsTimeline(@RequestParam(defaultValue = "all") String namespace,
                                              @RequestParam(defaultValue = "200") int limit,
                                              @RequestParam(required = false) String clusterId) {
        Map<String, Object> eventsRaw = service.events(namespace, clusterId);
        List<Map<String, Object>> rows = eventTimelineRows(itemsFromPayload(eventsRaw), Math.max(1, Math.min(limit, 1000)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", Boolean.TRUE.equals(eventsRaw.get("live")));
        out.put("tool", "kubectl");
        out.put("resource", "events-timeline");
        out.put("namespace", namespace);
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("summary", Map.of("warnings", rows.stream().filter(r -> "Warning".equals(r.get("type"))).count(), "events", rows.size()));
        out.put("toolStatus", eventsRaw.get("toolStatus"));
        out.put("realDataOnly", true);
        return out;
    }



    @GetMapping("/metrics/nodes")
    public Map<String, Object> nodeMetrics(@RequestParam(required = false) String clusterId) {
        ToolResult r = service.runKubectl("kubectl top nodes --no-headers", clusterId, 15);
        List<Map<String, Object>> items = r.ok() ? parseNodeTopRows(r.stdout()) : List.of();
        Map<String, Object> out = liveCommandPayload("node-metrics", "kubectl top nodes", r, clusterId);
        out.put("items", items);
        out.put("count", items.size());
        out.put("summary", Map.of("nodes", items.size(), "metricsServerAvailable", r.ok()));
        return out;
    }

    @GetMapping("/metrics/pods")
    public Map<String, Object> podMetrics(@RequestParam(defaultValue = "all") String namespace,
                                          @RequestParam(required = false) String clusterId) {
        String cmd = namespace == null || namespace.isBlank() || "all".equals(namespace)
            ? "kubectl top pods -A --no-headers"
            : "kubectl top pods -n " + safe(namespace) + " --no-headers";
        ToolResult r = service.runKubectl(cmd, clusterId, 20);
        List<Map<String, Object>> items = r.ok() ? parsePodTopRows(r.stdout(), namespace) : List.of();
        Map<String, Object> out = liveCommandPayload("pod-metrics", cmd, r, clusterId);
        out.put("namespace", namespace);
        out.put("items", items);
        out.put("count", items.size());
        out.put("summary", Map.of("pods", items.size(), "metricsServerAvailable", r.ok()));
        return out;
    }

    @GetMapping("/autoscaling/hpa")
    public Map<String, Object> hpaSummary(@RequestParam(defaultValue = "all") String namespace,
                                          @RequestParam(required = false) String clusterId) {
        Map<String, Object> hpa = service.resource("horizontalpodautoscalers", namespace, clusterId);
        List<Map<String, Object>> rows = hpaRows(itemsFromPayload(hpa));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", Boolean.TRUE.equals(hpa.get("live")));
        out.put("tool", "kubectl");
        out.put("resource", "horizontalpodautoscalers");
        out.put("namespace", namespace);
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("summary", Map.of("hpa", rows.size(), "withCurrentMetrics", rows.stream().filter(r -> !String.valueOf(r.get("currentMetrics")).isBlank()).count()));
        out.put("toolStatus", hpa.get("toolStatus"));
        out.put("realDataOnly", true);
        return out;
    }

    @GetMapping("/availability/pdb")
    public Map<String, Object> pdbSummary(@RequestParam(defaultValue = "all") String namespace,
                                          @RequestParam(required = false) String clusterId) {
        Map<String, Object> pdb = service.resource("poddisruptionbudgets", namespace, clusterId);
        List<Map<String, Object>> rows = pdbRows(itemsFromPayload(pdb));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", Boolean.TRUE.equals(pdb.get("live")));
        out.put("tool", "kubectl");
        out.put("resource", "poddisruptionbudgets");
        out.put("namespace", namespace);
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("summary", Map.of("pdb", rows.size(), "blockingDisruptions", rows.stream().filter(r -> "WARN".equals(r.get("status"))).count()));
        out.put("toolStatus", pdb.get("toolStatus"));
        out.put("realDataOnly", true);
        return out;
    }

    @GetMapping("/workload-owners")
    public Map<String, Object> workloadOwners(@RequestParam(defaultValue = "all") String namespace,
                                              @RequestParam(required = false) String clusterId) {
        Map<String, Object> pods = service.resource("pods", namespace, clusterId);
        List<Map<String, Object>> rows = ownerRows(itemsFromPayload(pods));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", Boolean.TRUE.equals(pods.get("live")));
        out.put("tool", "kubectl");
        out.put("resource", "workload-owners");
        out.put("namespace", namespace);
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("summary", Map.of("pods", rows.size(), "withoutOwner", rows.stream().filter(r -> "Pod".equals(r.get("ownerKind"))).count()));
        out.put("toolStatus", pods.get("toolStatus"));
        out.put("realDataOnly", true);
        return out;
    }

    @GetMapping("/api-resources")
    public Map<String, Object> apiResources(@RequestParam(required = false) String clusterId) {
        ToolResult r = service.runKubectl("kubectl api-resources -o wide --no-headers", clusterId, 20);
        List<Map<String, Object>> rows = r.ok() ? apiResourceRows(r.stdout()) : List.of();
        Map<String, Object> out = liveCommandPayload("api-resources", "kubectl api-resources", r, clusterId);
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("summary", Map.of("resources", rows.size(), "apiDiscoveryAvailable", r.ok()));
        return out;
    }

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
    // DAEMONSET / JOB / CRONJOB ACTIONS
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/daemonsets/{ns}/{name}/restart")
    public Map<String, Object> restartDaemonSet(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl rollout restart daemonset/" + safe(name) + " -n " + safe(ns),
                   "restart-daemonset", ns + "/" + name, 30);
    }

    @GetMapping("/daemonsets/{ns}/{name}/yaml")
    public Map<String, Object> daemonSetYaml(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl get daemonset " + safe(name) + " -n " + safe(ns) + " -o yaml", "get-yaml", ns + "/" + name, 10);
    }

    @GetMapping("/daemonsets/{ns}/{name}/describe")
    public Map<String, Object> describeDaemonSet(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl describe daemonset " + safe(name) + " -n " + safe(ns), "describe-daemonset", ns + "/" + name, 15);
    }

    @DeleteMapping("/jobs/{ns}/{name}")
    public Map<String, Object> deleteJob(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl delete job " + safe(name) + " -n " + safe(ns), "delete-job", ns + "/" + name, 30);
    }

    @GetMapping("/jobs/{ns}/{name}/yaml")
    public Map<String, Object> jobYaml(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl get job " + safe(name) + " -n " + safe(ns) + " -o yaml", "get-yaml", ns + "/" + name, 10);
    }

    @GetMapping("/jobs/{ns}/{name}/describe")
    public Map<String, Object> describeJob(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl describe job " + safe(name) + " -n " + safe(ns), "describe-job", ns + "/" + name, 15);
    }

    @PostMapping("/cronjobs/{ns}/{name}/suspend")
    public Map<String, Object> suspendCronJob(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl patch cronjob " + safe(name) + " -n " + safe(ns) + " -p '{\"spec\":{\"suspend\":true}}'",
                   "suspend-cronjob", ns + "/" + name, 20);
    }

    @PostMapping("/cronjobs/{ns}/{name}/resume")
    public Map<String, Object> resumeCronJob(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl patch cronjob " + safe(name) + " -n " + safe(ns) + " -p '{\"spec\":{\"suspend\":false}}'",
                   "resume-cronjob", ns + "/" + name, 20);
    }

    @GetMapping("/cronjobs/{ns}/{name}/yaml")
    public Map<String, Object> cronJobYaml(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl get cronjob " + safe(name) + " -n " + safe(ns) + " -o yaml", "get-yaml", ns + "/" + name, 10);
    }

    @GetMapping("/cronjobs/{ns}/{name}/describe")
    public Map<String, Object> describeCronJob(@PathVariable String ns, @PathVariable String name) {
        return run("kubectl describe cronjob " + safe(name) + " -n " + safe(ns), "describe-cronjob", ns + "/" + name, 15);
    }

    @GetMapping("/secrets")
    public Map<String, Object> maskedSecrets(@RequestParam(defaultValue = "all") String namespace,
                                             @RequestParam(required = false) String clusterId) {
        Map<String, Object> raw = service.resource("secrets", namespace, clusterId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> secret : itemsFromPayload(raw)) {
            Map<String, Object> copy = new LinkedHashMap<>(secret);
            Map metadata = asMap(secret.get("metadata"));
            Map<String, Object> data = new LinkedHashMap<>();
            Object rawData = secret.get("data");
            if (rawData instanceof Map<?,?> m) {
                for (Object key : m.keySet()) data.put(String.valueOf(key), "***MASKED***");
            }
            copy.put("data", data);
            copy.remove("stringData");
            copy.put("name", str(metadata.get("name")));
            copy.put("namespace", str(metadata.get("namespace")));
            copy.put("masked", true);
            items.add(copy);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", raw.get("live"));
        out.put("tool", "kubectl");
        out.put("resource", "secrets-masked");
        out.put("items", items);
        out.put("count", items.size());
        out.put("toolStatus", raw.get("toolStatus"));
        out.put("realDataOnly", true);
        out.put("note", "Secret keys are real, values are masked by the gateway before reaching the UI.");
        return out;
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

    private Map<String, Object> helmRun(String cmd, String action, String target, int timeout, String clusterId) {
        ToolResult r = service.runKubectl(cmd, clusterId, timeout);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", r.ok());
        payload.put("live", r.ok());
        payload.put("tool", "helm");
        payload.put("action", action);
        payload.put("target", target);
        payload.put("stdout", r.stdout());
        payload.put("stderr", r.stderr());
        payload.put("exitCode", r.exitCode());
        payload.put("durationMs", r.durationMs());
        payload.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
        payload.put("items", List.of(Map.of("name", target, "status", r.ok() ? "OK" : "ERROR", "stdout", r.stdout(), "stderr", r.stderr())));
        payload.put("toolStatus", r.asMap());
        payload.put("realDataOnly", true);
        payload.put("correlationId", events.mutation("HELM_" + action.toUpperCase(Locale.ROOT).replace('-', '_'), target, r.ok(), payload));
        return payload;
    }

    private Path portForwardDir() {
        return Path.of(System.getProperty("java.io.tmpdir"), "nebulaops-openlens-port-forward");
    }

    private List<Map<String, Object>> listPortForwards(String clusterIdFilter) {
        Path dir = portForwardDir();
        if (!Files.isDirectory(dir)) return List.of();
        List<Map<String, Object>> rows = new ArrayList<>();
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.properties")) {
            for (Path meta : stream) {
                Map<String, Object> row = portForwardRecord(meta, clusterIdFilter);
                if (row != null) rows.add(row);
            }
        } catch (Exception ignored) { }
        rows.sort(Comparator.comparing(r -> str(r.get("createdAt"))));
        return rows;
    }

    private Map<String, Object> portForwardRecord(Path meta, String clusterIdFilter) {
        try {
            Properties props = loadProps(meta);
            String clusterId = props.getProperty("clusterId", "current-context");
            if (clusterIdFilter != null && !clusterIdFilter.isBlank() && !"all".equals(clusterIdFilter) && !Objects.equals(clusterId, clusterIdFilter)) return null;
            String pid = props.getProperty("pid", "").trim();
            ToolResult alive = tools.shell("kill -0 " + safe(pid) + " >/dev/null 2>&1", 3);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", props.getProperty("id", meta.getFileName().toString().replace(".properties", "")));
            row.put("pid", pid);
            row.put("namespace", props.getProperty("namespace", "default"));
            row.put("kind", props.getProperty("kind", "service"));
            row.put("name", props.getProperty("name", ""));
            row.put("localPort", props.getProperty("localPort", ""));
            row.put("remotePort", props.getProperty("remotePort", ""));
            row.put("clusterId", clusterId);
            row.put("status", alive.ok() ? "RUNNING" : "STOPPED");
            row.put("live", alive.ok());
            row.put("url", "http://127.0.0.1:" + props.getProperty("localPort", ""));
            row.put("createdAt", props.getProperty("createdAt", ""));
            String logPath = props.getProperty("log", "");
            row.put("logTail", logPath.isBlank() ? List.of() : tail(Path.of(logPath), 20));
            row.put("toolStatus", alive.asMap());
            return row;
        } catch (Exception e) {
            return Map.of("id", meta.getFileName().toString(), "status", "ERROR", "live", false, "error", e.getMessage());
        }
    }

    private Properties loadProps(Path path) throws java.io.IOException {
        Properties props = new Properties();
        try (java.io.InputStream is = Files.newInputStream(path)) { props.load(is); }
        return props;
    }

    private List<String> tail(Path path, int limit) {
        try {
            if (path == null || !Files.exists(path)) return List.of();
            List<String> lines = Files.readAllLines(path);
            int start = Math.max(0, lines.size() - Math.max(1, limit));
            return lines.subList(start, lines.size());
        } catch (Exception e) {
            return List.of("log unavailable: " + e.getMessage());
        }
    }

    private String shellQuote(String s) {
        return "'" + String.valueOf(s).replace("'", "'\\''") + "'";
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LIVE DATA HELPERS
    // ═══════════════════════════════════════════════════════════════════════



    private Map<String, Object> liveCommandPayload(String resource, String command, ToolResult r, String clusterId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", r.ok());
        out.put("tool", "kubectl");
        out.put("resource", resource);
        out.put("command", command);
        out.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
        out.put("toolStatus", r.asMap());
        out.put("realDataOnly", true);
        if (!r.ok()) out.put("error", firstNonBlank(r.stderr(), r.message(), "kubectl command unavailable"));
        return out;
    }

    private List<Map<String, Object>> parseNodeTopRows(String stdout) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : String.valueOf(stdout).split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 5) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", parts[0]);
            row.put("name", parts[0]);
            row.put("cpuCores", parts[1]);
            row.put("cpuPercent", parts[2]);
            row.put("memoryBytes", parts[3]);
            row.put("memoryPercent", parts[4]);
            row.put("source", "kubectl top nodes");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> parsePodTopRows(String stdout, String namespace) {
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean all = namespace == null || namespace.isBlank() || "all".equals(namespace);
        for (String line : String.valueOf(stdout).split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;
            String[] parts = trimmed.split("\\s+");
            if ((all && parts.length < 4) || (!all && parts.length < 3)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            int offset = all ? 1 : 0;
            row.put("namespace", all ? parts[0] : namespace);
            row.put("name", parts[offset]);
            row.put("id", (all ? parts[0] + "/" : String.valueOf(namespace) + "/") + parts[offset]);
            row.put("cpuCores", parts[offset + 1]);
            row.put("memoryBytes", parts[offset + 2]);
            row.put("source", "kubectl top pods");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> hpaRows(List<Map<String, Object>> hpas) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> hpa : hpas) {
            Map meta = asMap(hpa.get("metadata"));
            Map spec = asMap(hpa.get("spec"));
            Map status = asMap(hpa.get("status"));
            Map scaleTarget = asMap(spec.get("scaleTargetRef"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", str(meta.get("namespace")) + "/" + str(meta.get("name")));
            row.put("namespace", str(meta.get("namespace")));
            row.put("name", str(meta.get("name")));
            row.put("target", str(scaleTarget.get("kind")) + "/" + str(scaleTarget.get("name")));
            row.put("minReplicas", spec.get("minReplicas"));
            row.put("maxReplicas", spec.get("maxReplicas"));
            row.put("currentReplicas", status.get("currentReplicas"));
            row.put("desiredReplicas", status.get("desiredReplicas"));
            row.put("currentMetrics", status.getOrDefault("currentMetrics", List.of()));
            row.put("status", Objects.equals(status.get("currentReplicas"), status.get("desiredReplicas")) ? "OK" : "SCALING");
            row.put("source", "kubectl");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> pdbRows(List<Map<String, Object>> pdbs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> pdb : pdbs) {
            Map meta = asMap(pdb.get("metadata"));
            Map spec = asMap(pdb.get("spec"));
            Map status = asMap(pdb.get("status"));
            int disruptionsAllowed = intValue(status.get("disruptionsAllowed"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", str(meta.get("namespace")) + "/" + str(meta.get("name")));
            row.put("namespace", str(meta.get("namespace")));
            row.put("name", str(meta.get("name")));
            row.put("minAvailable", spec.get("minAvailable"));
            row.put("maxUnavailable", spec.get("maxUnavailable"));
            row.put("currentHealthy", status.get("currentHealthy"));
            row.put("desiredHealthy", status.get("desiredHealthy"));
            row.put("disruptionsAllowed", disruptionsAllowed);
            row.put("status", disruptionsAllowed > 0 ? "OK" : "WARN");
            row.put("source", "kubectl");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> ownerRows(List<Map<String, Object>> pods) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> pod : pods) {
            Map meta = asMap(pod.get("metadata"));
            String ns = str(meta.getOrDefault("namespace", "default"));
            String name = str(meta.get("name"));
            Object owners = meta.get("ownerReferences");
            String ownerKind = "Pod";
            String ownerName = name;
            if (owners instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?,?> owner) {
                ownerKind = str(owner.get("kind"));
                ownerName = str(owner.get("name"));
            }
            Map status = asMap(pod.get("status"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", ns + "/" + name);
            row.put("namespace", ns);
            row.put("pod", name);
            row.put("ownerKind", ownerKind);
            row.put("ownerName", ownerName);
            row.put("phase", str(status.get("phase")));
            row.put("source", "kubectl");
            rows.add(row);
        }
        rows.sort(Comparator.comparing(r -> str(r.get("namespace")) + "/" + str(r.get("ownerKind")) + "/" + str(r.get("ownerName"))));
        return rows;
    }

    private List<Map<String, Object>> apiResourceRows(String stdout) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : String.valueOf(stdout).split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;
            String[] parts = trimmed.split("\\s+", 6);
            if (parts.length < 5) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", parts[0]);
            row.put("name", parts[0]);
            row.put("shortNames", parts.length > 1 ? parts[1] : "");
            row.put("apiGroup", parts.length > 2 ? parts[2] : "");
            row.put("namespaced", parts.length > 3 ? parts[3] : "");
            row.put("kind", parts.length > 4 ? parts[4] : "");
            row.put("verbs", parts.length > 5 ? parts[5] : "");
            row.put("source", "kubectl api-resources");
            rows.add(row);
        }
        return rows;
    }

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

    private Map<String, Object> kubectlJson(String cmd, String clusterId, int timeout) {
        ToolResult r = service.runKubectl(cmd, clusterId, timeout);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", r.ok());
        out.put("stdout", r.stdout());
        out.put("stderr", r.stderr());
        out.put("exitCode", r.exitCode());
        out.put("toolStatus", r.asMap());
        out.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
        return out;
    }

    private List<Map<String, Object>> storageSummaryItems(Map<String, Object> pvcs, Map<String, Object> pvs,
                                                           Map<String, Object> storageClasses, Map<String, Object> quotas) {
        List<Map<String, Object>> rows = new ArrayList<>();
        addResourceSummary(rows, "PersistentVolumeClaims", "persistentvolumeclaims", pvcs);
        addResourceSummary(rows, "PersistentVolumes", "persistentvolumes", pvs);
        addResourceSummary(rows, "StorageClasses", "storageclasses", storageClasses);
        addResourceSummary(rows, "ResourceQuotas", "resourcequotas", quotas);
        return rows;
    }

    private List<Map<String, Object>> networkSummaryItems(Map<String, Object> services, Map<String, Object> endpoints,
                                                          Map<String, Object> ingress, Map<String, Object> networkPolicies) {
        List<Map<String, Object>> rows = new ArrayList<>();
        addResourceSummary(rows, "Services", "services", services);
        addResourceSummary(rows, "Endpoints", "endpoints", endpoints);
        addResourceSummary(rows, "Ingress", "ingress", ingress);
        addResourceSummary(rows, "NetworkPolicies", "networkpolicies", networkPolicies);
        return rows;
    }

    private List<Map<String, Object>> quotaSummaryItems(Map<String, Object> quotas, Map<String, Object> limits) {
        List<Map<String, Object>> rows = new ArrayList<>();
        addResourceSummary(rows, "ResourceQuotas", "resourcequotas", quotas);
        addResourceSummary(rows, "LimitRanges", "limitranges", limits);
        return rows;
    }

    private void addResourceSummary(List<Map<String, Object>> rows, String label, String resource, Map<String, Object> payload) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", resource);
        row.put("name", label);
        row.put("resource", resource);
        row.put("status", Boolean.TRUE.equals(payload.get("live")) ? "LIVE" : "UNAVAILABLE");
        row.put("count", itemsFromPayload(payload).size());
        row.put("toolStatus", payload.get("toolStatus"));
        rows.add(row);
    }


    private List<Map<String, Object>> kubernetesSecurityFindings(List<Map<String, Object>> pods) {
        List<Map<String, Object>> findings = new ArrayList<>();
        for (Map<String, Object> pod : pods) {
            Map meta = asMap(pod.get("metadata"));
            Map spec = asMap(pod.get("spec"));
            String ns = str(meta.getOrDefault("namespace", "default"));
            String name = str(meta.getOrDefault("name", "pod"));
            if (Boolean.TRUE.equals(spec.get("hostNetwork"))) addSecurityFinding(findings, "host-network", "ERROR", ns, "Pod", name, "Pod uses hostNetwork", pod);
            if (Boolean.TRUE.equals(spec.get("hostPID"))) addSecurityFinding(findings, "host-pid", "ERROR", ns, "Pod", name, "Pod uses hostPID", pod);
            if (Boolean.TRUE.equals(spec.get("hostIPC"))) addSecurityFinding(findings, "host-ipc", "ERROR", ns, "Pod", name, "Pod uses hostIPC", pod);
            Object volumes = spec.get("volumes");
            if (volumes instanceof List<?> volumeRows) {
                for (Object vo : volumeRows) {
                    if (!(vo instanceof Map<?,?> v)) continue;
                    if (v.containsKey("hostPath")) addSecurityFinding(findings, "hostpath-volume", "WARN", ns, "Pod", name, "Pod uses hostPath volume " + str(v.get("name")), pod);
                }
            }
            inspectPodContainersForSecurity(findings, ns, name, "containers", spec.get("containers"), pod);
            inspectPodContainersForSecurity(findings, ns, name, "initContainers", spec.get("initContainers"), pod);
        }
        findings.sort(Comparator.comparing(r -> severityWeight(str(r.get("severity")))));
        return findings;
    }

    private void inspectPodContainersForSecurity(List<Map<String, Object>> findings, String ns, String podName, String group, Object containers, Map<String, Object> pod) {
        if (!(containers instanceof List<?> rows)) return;
        for (Object co : rows) {
            if (!(co instanceof Map<?,?> c)) continue;
            String cname = str(c.get("name"));
            Map resources = asMap(c.get("resources"));
            Map limits = asMap(resources.get("limits"));
            Map requests = asMap(resources.get("requests"));
            if (limits.isEmpty()) addSecurityFinding(findings, "missing-limits", "WARN", ns, "Container", podName + "/" + cname, group + " has no resource limits", pod);
            if (requests.isEmpty()) addSecurityFinding(findings, "missing-requests", "WARN", ns, "Container", podName + "/" + cname, group + " has no resource requests", pod);
            Map sc = asMap(c.get("securityContext"));
            if (Boolean.TRUE.equals(sc.get("privileged"))) addSecurityFinding(findings, "privileged", "ERROR", ns, "Container", podName + "/" + cname, "Container is privileged", pod);
            if (Boolean.TRUE.equals(sc.get("allowPrivilegeEscalation"))) addSecurityFinding(findings, "privilege-escalation", "WARN", ns, "Container", podName + "/" + cname, "Container allows privilege escalation", pod);
            if (Boolean.FALSE.equals(sc.get("runAsNonRoot"))) addSecurityFinding(findings, "run-as-root", "WARN", ns, "Container", podName + "/" + cname, "Container explicitly allows running as root", pod);
            Map caps = asMap(sc.get("capabilities"));
            Object add = caps.get("add");
            if (add instanceof List<?> capRows && !capRows.isEmpty()) addSecurityFinding(findings, "capabilities-add", "WARN", ns, "Container", podName + "/" + cname, "Container adds Linux capabilities: " + capRows, pod);
        }
    }

    private void addSecurityFinding(List<Map<String, Object>> findings, String idPrefix, String severity, String namespace, String kind, String name, String message, Object raw) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", idPrefix + ":" + namespace + ":" + kind + ":" + name);
        row.put("severity", severity);
        row.put("namespace", namespace);
        row.put("kind", kind);
        row.put("name", name);
        row.put("message", message);
        row.put("source", "kubectl");
        row.put("raw", raw);
        findings.add(row);
    }

    private List<Map<String, Object>> ingressTlsFindings(List<Map<String, Object>> ingresses) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> ingress : ingresses) {
            Map meta = asMap(ingress.get("metadata"));
            Map spec = asMap(ingress.get("spec"));
            String ns = str(meta.getOrDefault("namespace", "default"));
            String name = str(meta.getOrDefault("name", "ingress"));
            Object tls = spec.get("tls");
            boolean hasTls = tls instanceof List<?> list && !list.isEmpty();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", ns + "/" + name);
            row.put("namespace", ns);
            row.put("name", name);
            row.put("tls", hasTls ? "CONFIGURED" : "MISSING");
            row.put("severity", hasTls ? "OK" : "WARN");
            row.put("message", hasTls ? "Ingress TLS is configured" : "Ingress has no TLS section");
            row.put("tlsEntries", hasTls ? tls : List.of());
            row.put("source", "kubectl");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> serviceEndpointRows(List<Map<String, Object>> services, List<Map<String, Object>> endpoints) {
        Map<String, Map<String, Object>> byKey = new HashMap<>();
        for (Map<String, Object> ep : endpoints) {
            Map meta = asMap(ep.get("metadata"));
            byKey.put(str(meta.get("namespace")) + "/" + str(meta.get("name")), ep);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> svc : services) {
            Map meta = asMap(svc.get("metadata"));
            Map spec = asMap(svc.get("spec"));
            String ns = str(meta.getOrDefault("namespace", "default"));
            String name = str(meta.getOrDefault("name", "service"));
            Map<String, Object> ep = byKey.get(ns + "/" + name);
            int ready = endpointAddressCount(ep == null ? null : ep.get("subsets"), "addresses");
            int notReady = endpointAddressCount(ep == null ? null : ep.get("subsets"), "notReadyAddresses");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", ns + "/" + name);
            row.put("namespace", ns);
            row.put("name", name);
            row.put("type", str(spec.getOrDefault("type", "ClusterIP")));
            row.put("readyEndpoints", ready);
            row.put("notReadyEndpoints", notReady);
            row.put("status", ready > 0 || "ExternalName".equals(str(spec.get("type"))) ? "OK" : "WARN");
            row.put("source", "kubectl");
            row.put("endpoint", ep == null ? Map.of() : ep);
            rows.add(row);
        }
        return rows;
    }

    private int endpointAddressCount(Object subsets, String key) {
        int count = 0;
        if (subsets instanceof List<?> rows) {
            for (Object so : rows) {
                if (!(so instanceof Map<?,?> subset)) continue;
                Object addresses = subset.get(key);
                if (addresses instanceof List<?> list) count += list.size();
            }
        }
        return count;
    }

    private List<Map<String, Object>> eventTimelineRows(List<Map<String, Object>> events, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> event : events) {
            Map meta = asMap(event.get("metadata"));
            Map involved = asMap(event.get("involvedObject"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", str(meta.getOrDefault("uid", meta.getOrDefault("name", UUID.randomUUID().toString()))));
            row.put("time", firstNonBlank(str(event.get("lastTimestamp")), str(event.get("eventTime")), str(event.get("firstTimestamp")), str(meta.get("creationTimestamp"))));
            row.put("type", str(event.get("type")));
            row.put("reason", str(event.get("reason")));
            row.put("namespace", firstNonBlank(str(involved.get("namespace")), str(meta.get("namespace"))));
            row.put("kind", str(involved.get("kind")));
            row.put("name", str(involved.get("name")));
            row.put("message", str(event.get("message")));
            row.put("source", "kubectl");
            rows.add(row);
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> str(r.get("time"))).reversed());
        if (rows.size() > limit) return new ArrayList<>(rows.subList(0, limit));
        return rows;
    }

    private List<Map<String, Object>> detectProblems(String namespace, Map<String, Object> pods, Map<String, Object> eventsRaw,
                                                     Map<String, Object> nodes, Map<String, Object> pvcs,
                                                     Map<String, Object> endpoints, Map<String, Object> services) {
        List<Map<String, Object>> problems = new ArrayList<>();
        for (Map<String, Object> pod : itemsFromPayload(pods)) detectPodProblems(problems, pod);
        for (Map<String, Object> event : itemsFromPayload(eventsRaw)) detectEventProblems(problems, event);
        for (Map<String, Object> node : itemsFromPayload(nodes)) detectNodeProblems(problems, node);
        for (Map<String, Object> pvc : itemsFromPayload(pvcs)) detectPvcProblems(problems, pvc);
        detectServiceEndpointProblems(problems, itemsFromPayload(services), itemsFromPayload(endpoints));
        problems.sort(Comparator.comparing(p -> severityWeight(str(p.get("severity")))));
        return problems;
    }

    private void detectPodProblems(List<Map<String, Object>> problems, Map<String, Object> pod) {
        Map meta = asMap(pod.get("metadata"));
        Map status = asMap(pod.get("status"));
        String ns = str(meta.getOrDefault("namespace", "default"));
        String name = str(meta.getOrDefault("name", "pod"));
        String phase = str(status.get("phase"));
        if (List.of("Pending", "Failed", "Unknown").contains(phase)) {
            addProblem(problems, "pod-phase", severityForPhase(phase), ns, "Pod", name, "Pod phase is " + phase, phase, pod);
        }
        Object containerStatuses = status.get("containerStatuses");
        if (containerStatuses instanceof List<?> rows) {
            for (Object o : rows) {
                if (!(o instanceof Map<?,?> cs)) continue;
                String cname = str(cs.get("name"));
                int restarts = intValue(cs.get("restartCount"));
                if (restarts >= 5) addProblem(problems, "restart-count", "WARN", ns, "Pod", name, "Container " + cname + " restarted " + restarts + " times", String.valueOf(restarts), pod);
                Map state = asMap(cs.get("state"));
                Map waiting = asMap(state.get("waiting"));
                Map terminated = asMap(state.get("terminated"));
                String reason = firstNonBlank(str(waiting.get("reason")), str(terminated.get("reason")));
                if (List.of("CrashLoopBackOff", "ImagePullBackOff", "ErrImagePull", "CreateContainerConfigError", "RunContainerError", "OOMKilled").contains(reason)) {
                    addProblem(problems, "container-state", reason.equals("OOMKilled") ? "ERROR" : "ERROR", ns, "Pod", name, "Container " + cname + " state: " + reason, reason, pod);
                }
            }
        }
    }

    private void detectEventProblems(List<Map<String, Object>> problems, Map<String, Object> event) {
        String type = str(event.get("type"));
        if (!"Warning".equalsIgnoreCase(type)) return;
        Map involved = asMap(event.get("involvedObject"));
        String ns = str(involved.getOrDefault("namespace", event.getOrDefault("metadata", Map.of())));
        if (ns.startsWith("{")) ns = str(asMap(event.get("metadata")).getOrDefault("namespace", "default"));
        String kind = str(involved.getOrDefault("kind", "Event"));
        String name = str(involved.getOrDefault("name", "kubernetes"));
        String reason = str(event.getOrDefault("reason", "Warning"));
        String message = str(event.getOrDefault("message", "Kubernetes warning event"));
        addProblem(problems, "warning-event", "WARN", ns, kind, name, reason + ": " + message, reason, event);
    }

    private void detectNodeProblems(List<Map<String, Object>> problems, Map<String, Object> node) {
        Map meta = asMap(node.get("metadata"));
        Map status = asMap(node.get("status"));
        String name = str(meta.getOrDefault("name", "node"));
        Object conditions = status.get("conditions");
        if (conditions instanceof List<?> rows) {
            for (Object o : rows) {
                if (!(o instanceof Map<?,?> c)) continue;
                String type = str(c.get("type"));
                String value = str(c.get("status"));
                boolean problem = ("Ready".equals(type) && !"True".equals(value)) || (!"Ready".equals(type) && "True".equals(value));
                if (problem) addProblem(problems, "node-condition", "ERROR", "cluster", "Node", name, "Node condition " + type + "=" + value + ": " + str(c.get("message")), type, node);
            }
        }
    }

    private void detectPvcProblems(List<Map<String, Object>> problems, Map<String, Object> pvc) {
        Map meta = asMap(pvc.get("metadata"));
        Map status = asMap(pvc.get("status"));
        String phase = str(status.get("phase"));
        if (phase != null && !phase.isBlank() && !"Bound".equals(phase)) {
            addProblem(problems, "pvc-phase", "WARN", str(meta.getOrDefault("namespace", "default")), "PersistentVolumeClaim", str(meta.getOrDefault("name", "pvc")), "PVC phase is " + phase, phase, pvc);
        }
    }

    private void detectServiceEndpointProblems(List<Map<String, Object>> problems, List<Map<String, Object>> services, List<Map<String, Object>> endpoints) {
        Map<String, Map<String, Object>> endpointsByKey = new HashMap<>();
        for (Map<String, Object> ep : endpoints) {
            Map meta = asMap(ep.get("metadata"));
            endpointsByKey.put(str(meta.get("namespace")) + "/" + str(meta.get("name")), ep);
        }
        for (Map<String, Object> svc : services) {
            Map meta = asMap(svc.get("metadata"));
            Map spec = asMap(svc.get("spec"));
            String type = str(spec.get("type"));
            if ("ExternalName".equals(type)) continue;
            String ns = str(meta.getOrDefault("namespace", "default"));
            String name = str(meta.getOrDefault("name", "service"));
            Map<String, Object> ep = endpointsByKey.get(ns + "/" + name);
            Object subsets = ep == null ? null : ep.get("subsets");
            if (!(subsets instanceof List<?> rows) || rows.isEmpty()) {
                addProblem(problems, "service-endpoints", "WARN", ns, "Service", name, "Service has no ready endpoint subsets", "NO_ENDPOINTS", svc);
            }
        }
    }

    private void addProblem(List<Map<String, Object>> problems, String idPrefix, String severity, String namespace,
                            String kind, String name, String message, String reason, Object raw) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", idPrefix + ":" + namespace + ":" + kind + ":" + name + ":" + reason);
        row.put("severity", severity);
        row.put("namespace", namespace == null || namespace.isBlank() ? "default" : namespace);
        row.put("kind", kind);
        row.put("name", name);
        row.put("reason", reason);
        row.put("message", message);
        row.put("source", "kubectl");
        row.put("raw", raw);
        problems.add(row);
    }

    private Map<String, Object> problemSummary(List<Map<String, Object>> problems) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", problems.size());
        out.put("errors", problems.stream().filter(p -> "ERROR".equals(p.get("severity"))).count());
        out.put("warnings", problems.stream().filter(p -> "WARN".equals(p.get("severity"))).count());
        out.put("info", problems.stream().filter(p -> "INFO".equals(p.get("severity"))).count());
        return out;
    }

    private List<Map<String, Object>> itemsFromPayload(Map<String, Object> payload) {
        Object data = payload == null ? null : payload.get("data");
        if (data instanceof Map<?,?> m) {
            Object items = m.get("items");
            if (items instanceof List<?> rows) return mapRows(rows);
        }
        Object items = payload == null ? null : payload.get("items");
        if (items instanceof List<?> rows) return mapRows(rows);
        return List.of();
    }

    private List<Map<String, Object>> mapRows(List<?> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object row : rows) if (row instanceof Map<?,?> m) out.add((Map<String, Object>) m);
        return out;
    }

    private int severityWeight(String severity) {
        if ("ERROR".equals(severity)) return 0;
        if ("WARN".equals(severity)) return 1;
        return 2;
    }

    private String severityForPhase(String phase) {
        return "Failed".equals(phase) || "Unknown".equals(phase) ? "ERROR" : "WARN";
    }

    private int intValue(Object value) {
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return 0; }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    private String safeKindForCommand(String kind) {
        String k = safe(kind).toLowerCase(Locale.ROOT);
        if (k.equals("deployment") || k.equals("deploy")) return "deployment";
        if (k.equals("statefulset") || k.equals("sts")) return "statefulset";
        if (k.equals("daemonset") || k.equals("ds")) return "daemonset";
        if (k.equals("job")) return "job";
        if (k.equals("cronjob") || k.equals("cj")) return "cronjob";
        if (k.equals("pod") || k.equals("po")) return "pod";
        if (k.equals("service") || k.equals("svc")) return "service";
        if (k.equals("ingress") || k.equals("ing")) return "ingress";
        if (k.equals("configmap") || k.equals("cm")) return "configmap";
        if (k.equals("secret")) return "secret";
        if (k.equals("persistentvolumeclaim") || k.equals("pvc")) return "persistentvolumeclaim";
        return k;
    }

    private Map<String, Object> troubleshootingPayload(String resource, String namespace, String name, String clusterId, List<Map<String, Object>> findings) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", true); out.put("tool", "kubectl"); out.put("resource", resource); out.put("namespace", namespace); out.put("name", name);
        out.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
        out.put("items", findings); out.put("count", findings.size()); out.put("summary", rootCauseSummary(findings)); out.put("realDataOnly", true);
        return out;
    }

    private Map<String, Object> rootCauseSummary(List<Map<String, Object>> findings) {
        long errors = findings.stream().filter(f -> "ERROR".equals(f.get("severity"))).count();
        long warnings = findings.stream().filter(f -> "WARN".equals(f.get("severity"))).count();
        return Map.of("total", findings.size(), "errors", errors, "warnings", warnings, "status", errors > 0 ? "ERROR" : warnings > 0 ? "WARN" : "OK");
    }

    private List<Map<String, Object>> rootCauseFindings(String target, String... texts) {
        List<Map<String, Object>> findings = new ArrayList<>();
        addTextFinding(findings, target, "ERROR", "IMAGE_PULL", "Image pull failure detected", texts, "imagepullbackoff", "errimagepull", "pull access denied", "failed to pull image");
        addTextFinding(findings, target, "ERROR", "CRASH_LOOP", "Crash loop detected", texts, "crashloopbackoff", "back-off restarting failed container");
        addTextFinding(findings, target, "ERROR", "OOM_KILLED", "Out-of-memory termination detected", texts, "oomkilled", "out of memory", "exit code 137");
        addTextFinding(findings, target, "ERROR", "FAILED_SCHEDULING", "Scheduling failure detected", texts, "failedscheduling", "insufficient cpu", "insufficient memory", "didn't match pod anti-affinity", "node(s) had taint");
        addTextFinding(findings, target, "ERROR", "MISSING_SECRET", "Missing Secret detected", texts, "secret ", "not found", "couldn't find key");
        addTextFinding(findings, target, "ERROR", "MISSING_CONFIGMAP", "Missing ConfigMap detected", texts, "configmap", "not found");
        addTextFinding(findings, target, "WARN", "READINESS_PROBE", "Readiness probe failures detected", texts, "readiness probe failed", "unready");
        addTextFinding(findings, target, "WARN", "LIVENESS_PROBE", "Liveness probe failures detected", texts, "liveness probe failed");
        addTextFinding(findings, target, "WARN", "PVC_PENDING", "PersistentVolumeClaim pending or unbound", texts, "persistentvolumeclaim", "pending", "unbound immediate persistentvolumeclaims");
        addTextFinding(findings, target, "WARN", "CONNECTION_REFUSED", "Application connection refused in logs", texts, "connection refused");
        addTextFinding(findings, target, "WARN", "TIMEOUT", "Timeout detected in logs/events", texts, "timed out", "timeout");
        return findings;
    }

    private void addTextFinding(List<Map<String, Object>> findings, String target, String severity, String reason, String message, String[] texts, String... needles) {
        List<String> evidence = new ArrayList<>();
        for (String text : texts) {
            String safeText = text == null ? "" : text;
            String lower = safeText.toLowerCase(Locale.ROOT);
            boolean matched = false;
            for (String needle : needles) if (lower.contains(needle.toLowerCase(Locale.ROOT))) { matched = true; break; }
            if (!matched) continue;
            for (String line : safeText.split("\\R")) {
                String l = line.toLowerCase(Locale.ROOT);
                for (String needle : needles) if (l.contains(needle.toLowerCase(Locale.ROOT))) {
                    evidence.add(line.trim());
                    break;
                }
                if (evidence.size() >= 5) break;
            }
            if (evidence.size() >= 5) break;
        }
        if (!evidence.isEmpty()) findings.add(kubeFinding(severity, reason, target, message, evidence));
    }

    private Map<String, Object> kubeFinding(String severity, String reason, String target, String message, List<String> evidence) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", reason + ":" + target); row.put("severity", severity); row.put("reason", reason); row.put("target", target); row.put("message", message); row.put("evidence", evidence); row.put("source", "kubectl");
        return row;
    }

    private List<Map<String, Object>> workloadPodFindings(String kind, String name, List<Map<String, Object>> pods) {
        List<Map<String, Object>> findings = new ArrayList<>();
        for (Map<String, Object> pod : pods) {
            Map meta = asMap(pod.get("metadata"));
            String podName = str(meta.get("name"));
            String owners = str(meta.get("ownerReferences"));
            if (!podName.contains(name) && !owners.contains(name)) continue;
            Map status = asMap(pod.get("status"));
            String phase = str(status.get("phase"));
            if (List.of("Pending", "Failed", "Unknown").contains(phase)) findings.add(kubeFinding("ERROR", "POD_" + phase.toUpperCase(Locale.ROOT), podName, "Owned pod phase is " + phase, List.of()));
        }
        return findings;
    }

    private Map<String, Object> findByName(List<Map<String, Object>> rows, String namespace, String name) {
        for (Map<String, Object> row : rows) {
            Map meta = asMap(row.get("metadata"));
            if (name.equals(str(meta.get("name"))) && (namespace == null || namespace.isBlank() || namespace.equals(str(meta.getOrDefault("namespace", namespace))))) return row;
        }
        return Map.of();
    }

    private List<Map<String, Object>> serviceConnectivityFindings(String ns, String name, Map<String, Object> svc, Map<String, Object> ep, List<Map<String, Object>> pods, List<Map<String, Object>> ingresses, List<Map<String, Object>> networkPolicies) {
        List<Map<String, Object>> findings = new ArrayList<>();
        String target = ns + "/" + name;
        if (svc == null || svc.isEmpty()) { findings.add(kubeFinding("ERROR", "SERVICE_NOT_FOUND", target, "Service was not returned by kubectl", List.of())); return findings; }
        int ready = endpointAddressCount(ep.get("subsets"), "addresses");
        int notReady = endpointAddressCount(ep.get("subsets"), "notReadyAddresses");
        if (ready == 0 && !"ExternalName".equals(str(asMap(svc.get("spec")).get("type")))) findings.add(kubeFinding("ERROR", "NO_READY_ENDPOINTS", target, "Service has no ready endpoints", List.of()));
        if (notReady > 0) findings.add(kubeFinding("WARN", "NOT_READY_ENDPOINTS", target, "Service has " + notReady + " not-ready endpoint addresses", List.of()));
        List<Map<String, Object>> matchedPods = matchingPodsForService(svc, pods);
        if (matchedPods.isEmpty() && ready == 0) findings.add(kubeFinding("ERROR", "SELECTOR_MATCHES_NO_PODS", target, "Service selector does not match any current pod", List.of()));
        if (matchingIngressesForService(name, ingresses).isEmpty()) findings.add(kubeFinding("WARN", "NO_INGRESS", target, "No Ingress backend references this service", List.of()));
        if (!networkPolicies.isEmpty()) findings.add(kubeFinding("WARN", "NETWORK_POLICIES_PRESENT", target, "NetworkPolicies exist in this namespace; review ingress/egress rules if traffic is blocked", List.of()));
        return findings;
    }

    private List<Map<String, Object>> ingressConnectivityFindings(String ns, String name, Map<String, Object> ingress, List<Map<String, Object>> services, List<Map<String, Object>> endpoints) {
        List<Map<String, Object>> findings = new ArrayList<>();
        String target = ns + "/" + name;
        if (ingress == null || ingress.isEmpty()) { findings.add(kubeFinding("ERROR", "INGRESS_NOT_FOUND", target, "Ingress was not returned by kubectl", List.of())); return findings; }
        Map spec = asMap(ingress.get("spec"));
        Object tls = spec.get("tls");
        if (!(tls instanceof List<?> list) || list.isEmpty()) findings.add(kubeFinding("WARN", "TLS_NOT_CONFIGURED", target, "Ingress has no TLS section", List.of()));
        for (String serviceName : backendServiceNames(ingress)) {
            Map<String, Object> svc = findByName(services, ns, serviceName);
            if (svc.isEmpty()) findings.add(kubeFinding("ERROR", "BACKEND_SERVICE_MISSING", target, "Ingress backend service is missing: " + serviceName, List.of()));
            Map<String, Object> ep = findByName(endpoints, ns, serviceName);
            if (!svc.isEmpty() && endpointAddressCount(ep.get("subsets"), "addresses") == 0) findings.add(kubeFinding("ERROR", "BACKEND_WITHOUT_ENDPOINTS", target, "Ingress backend service has no ready endpoints: " + serviceName, List.of()));
        }
        return findings;
    }

    private List<Map<String, Object>> matchingPodsForService(Map<String, Object> svc, List<Map<String, Object>> pods) {
        Map spec = asMap(svc.get("spec"));
        Map selector = asMap(spec.get("selector"));
        if (selector.isEmpty()) return List.of();
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map<String, Object> pod : pods) {
            Map meta = asMap(pod.get("metadata"));
            Map labels = asMap(meta.get("labels"));
            boolean ok = true;
            for (Object key : selector.keySet()) if (!Objects.equals(str(selector.get(key)), str(labels.get(key)))) { ok = false; break; }
            if (ok) matches.add(pod);
        }
        return matches;
    }




    private List<String> servicePortSummary(Map spec) {
        List<String> ports = new ArrayList<>();
        Object raw = spec == null ? null : spec.get("ports");
        if (raw instanceof List<?> rows) {
            for (Object ro : rows) {
                Map row = asMap(ro);
                String protocol = firstNonBlank(str(row.get("protocol")), "TCP");
                String port = str(row.get("port"));
                String targetPort = str(row.get("targetPort"));
                String name = str(row.get("name"));
                String value = protocol + ":" + port + (targetPort.isBlank() ? "" : "→" + targetPort) + (name.isBlank() ? "" : " (" + name + ")");
                if (!port.isBlank()) ports.add(value);
            }
        }
        return ports;
    }

    private List<String> endpointPodTargetNames(Object subsets) {
        Set<String> names = new TreeSet<>();
        if (subsets instanceof List<?> rows) {
            for (Object so : rows) {
                if (!(so instanceof Map<?,?> subset)) continue;
                collectEndpointPodTargetNames(names, subset.get("addresses"));
                collectEndpointPodTargetNames(names, subset.get("notReadyAddresses"));
            }
        }
        return new ArrayList<>(names);
    }

    private void collectEndpointPodTargetNames(Set<String> names, Object addresses) {
        if (addresses instanceof List<?> rows) {
            for (Object ao : rows) {
                Map address = asMap(ao);
                Map targetRef = asMap(address.get("targetRef"));
                if ("Pod".equals(str(targetRef.get("kind")))) {
                    String name = str(targetRef.get("name"));
                    if (!name.isBlank()) names.add(name);
                }
            }
        }
    }

    private List<Map<String, Object>> matchingPodsForServiceInNamespace(Map<String, Object> svc, List<Map<String, Object>> pods) {
        String serviceNamespace = str(asMap(svc.get("metadata")).getOrDefault("namespace", "default"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> pod : matchingPodsForService(svc, pods)) {
            String podNamespace = str(asMap(pod.get("metadata")).getOrDefault("namespace", "default"));
            if (Objects.equals(serviceNamespace, podNamespace)) out.add(pod);
        }
        return out;
    }

    private List<Map<String, Object>> matchingPodsForNetworkPolicy(Map<String, Object> policy, List<Map<String, Object>> pods) {
        String policyNamespace = str(asMap(policy.get("metadata")).getOrDefault("namespace", "default"));
        Map podSelector = asMap(asMap(policy.get("spec")).get("podSelector"));
        Map matchLabels = asMap(podSelector.get("matchLabels"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> pod : pods) {
            Map meta = asMap(pod.get("metadata"));
            String podNamespace = str(meta.getOrDefault("namespace", "default"));
            if (!Objects.equals(policyNamespace, podNamespace)) continue;
            if (matchLabels.isEmpty() || labelsContain(matchLabels, asMap(meta.get("labels")))) out.add(pod);
        }
        return out;
    }

    private boolean labelsContain(Map required, Map labels) {
        for (Object key : required.keySet()) {
            if (!Objects.equals(str(required.get(key)), str(labels.get(key)))) return false;
        }
        return true;
    }

    private List<String> ingressHosts(Map<String, Object> ingress) {
        List<String> hosts = new ArrayList<>();
        Object rules = asMap(ingress.get("spec")).get("rules");
        if (rules instanceof List<?> ruleRows) for (Object rule : ruleRows) {
            String host = str(asMap(rule).get("host"));
            if (!host.isBlank() && !hosts.contains(host)) hosts.add(host);
        }
        return hosts;
    }

    private String kubeNetworkId(String type, String namespace, String name) {
        return type + ":" + (namespace == null || namespace.isBlank() ? "default" : namespace) + ":" + name;
    }

    private Map<String, Object> kubeNetworkNode(String type, String id, String name, String namespace, Map<String, Object> details) {
        Map<String, Object> row = kubeNode(type, id, name);
        row.put("namespace", namespace == null || namespace.isBlank() ? "default" : namespace);
        row.put("details", details == null ? Map.of() : details);
        String status = str(details == null ? "" : details.get("phase"));
        if (status.isBlank()) status = str(details == null ? "" : details.get("type"));
        row.put("status", status.isBlank() ? "observed" : status);
        row.put("source", "kubectl");
        return row;
    }

    private Map<String, Object> kubeNetworkEdge(String from, String to, String relation, String status) {
        Map<String, Object> row = kubeEdge(from, to, relation);
        row.put("status", status == null || status.isBlank() ? "OK" : status);
        row.put("source", "kubectl");
        return row;
    }

    private List<Map<String, Object>> matchingIngressesForService(String serviceName, List<Map<String, Object>> ingresses) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> ingress : ingresses) if (backendServiceNames(ingress).contains(serviceName)) out.add(ingress);
        return out;
    }

    private List<String> backendServiceNames(Map<String, Object> ingress) {
        List<String> names = new ArrayList<>();
        Map spec = asMap(ingress.get("spec"));
        collectBackendServiceName(names, asMap(spec.get("defaultBackend")));
        Object rules = spec.get("rules");
        if (rules instanceof List<?> ruleRows) for (Object ro : ruleRows) {
            Map http = asMap(asMap(ro).get("http"));
            Object paths = http.get("paths");
            if (paths instanceof List<?> pathRows) for (Object po : pathRows) collectBackendServiceName(names, asMap(asMap(po).get("backend")));
        }
        return names;
    }

    private void collectBackendServiceName(List<String> names, Map backend) {
        Map service = asMap(backend.get("service"));
        String name = str(service.get("name"));
        if (!name.isBlank() && !names.contains(name)) names.add(name);
    }

    private Map<String, Object> dependencyMapFor(String ns, String kind, String name, String clusterId) {
        Map<String, Object> pods = service.resource("pods", ns, clusterId);
        Map<String, Object> services = service.resource("services", ns, clusterId);
        Map<String, Object> ingresses = service.resource("ingress", ns, clusterId);
        Map<String, Object> configmaps = service.resource("configmaps", ns, clusterId);
        Map<String, Object> secrets = service.resource("secrets", ns, clusterId);
        Map<String, Object> pvcs = service.resource("persistentvolumeclaims", ns, clusterId);
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        if (!"namespace".equals(kind)) nodes.add(kubeNode("workload", kind + ":" + ns + ":" + name, kind + "/" + name));
        for (Map<String, Object> pod : itemsFromPayload(pods)) {
            Map meta = asMap(pod.get("metadata"));
            String podName = str(meta.get("name"));
            if (!"namespace".equals(kind) && !podName.contains(name) && !str(meta.get("ownerReferences")).contains(name)) continue;
            nodes.add(kubeNode("pod", "pod:" + ns + ":" + podName, podName));
            if (!"namespace".equals(kind)) edges.add(kubeEdge(kind + ":" + ns + ":" + name, "pod:" + ns + ":" + podName, "owns"));
        }
        for (Map<String, Object> svc : itemsFromPayload(services)) {
            Map meta = asMap(svc.get("metadata")); String svcName = str(meta.get("name"));
            nodes.add(kubeNode("service", "service:" + ns + ":" + svcName, svcName));
            for (Map<String, Object> pod : matchingPodsForService(svc, itemsFromPayload(pods))) edges.add(kubeEdge("service:" + ns + ":" + svcName, "pod:" + ns + ":" + str(asMap(pod.get("metadata")).get("name")), "selects"));
        }
        for (Map<String, Object> ing : itemsFromPayload(ingresses)) {
            Map meta = asMap(ing.get("metadata")); String ingName = str(meta.get("name"));
            nodes.add(kubeNode("ingress", "ingress:" + ns + ":" + ingName, ingName));
            for (String svc : backendServiceNames(ing)) edges.add(kubeEdge("ingress:" + ns + ":" + ingName, "service:" + ns + ":" + svc, "routes-to"));
        }
        addResourceNodes(nodes, "configmap", ns, itemsFromPayload(configmaps));
        addResourceNodes(nodes, "secret", ns, itemsFromPayload(secrets));
        addResourceNodes(nodes, "pvc", ns, itemsFromPayload(pvcs));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", Boolean.TRUE.equals(pods.get("live")) || Boolean.TRUE.equals(services.get("live"))); out.put("tool", "kubectl"); out.put("resource", "dependency-map"); out.put("namespace", ns); out.put("kind", kind); out.put("name", name);
        out.put("nodes", dedupeKubeNodes(nodes)); out.put("edges", edges); out.put("items", dedupeKubeNodes(nodes)); out.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId); out.put("realDataOnly", true);
        return out;
    }

    private Map<String, Object> kubeNode(String type, String id, String name) { Map<String, Object> row = new LinkedHashMap<>(); row.put("id", id); row.put("type", type); row.put("name", name); return row; }
    private Map<String, Object> kubeEdge(String from, String to, String relation) { Map<String, Object> row = new LinkedHashMap<>(); row.put("from", from); row.put("to", to); row.put("relation", relation); return row; }
    private void addResourceNodes(List<Map<String, Object>> nodes, String type, String ns, List<Map<String, Object>> rows) { for (Map<String, Object> r : rows) nodes.add(kubeNode(type, type + ":" + ns + ":" + str(asMap(r.get("metadata")).get("name")), str(asMap(r.get("metadata")).get("name")))); }
    private List<Map<String, Object>> dedupeKubeNodes(List<Map<String, Object>> nodes) { Map<String, Map<String, Object>> byId = new LinkedHashMap<>(); for (Map<String, Object> n : nodes) byId.putIfAbsent(str(n.get("id")), n); return new ArrayList<>(byId.values()); }

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
