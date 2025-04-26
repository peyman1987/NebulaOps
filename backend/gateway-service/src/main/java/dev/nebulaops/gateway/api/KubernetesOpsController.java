package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.service.KubernetesPlatformService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * v21.1 — Kubernetes endpoints returning shapes Angular expects.
 */
@SuppressWarnings({"unchecked","rawtypes"})
@RestController
@RequestMapping("/api/kubernetes")
public class KubernetesOpsController {

    private final KubernetesPlatformService service;

    public KubernetesOpsController(KubernetesPlatformService service) {
        this.service = service;
    }

    /** Angular expects: { cluster, resources, logs, live } */
    @GetMapping("/snapshot")
    public Map<String, Object> snapshot() {
        Map nodesRaw = service.nodes();
        boolean connected = Boolean.TRUE.equals(nodesRaw.get("live"));

        List<Map<String, Object>> resources = new ArrayList<>();
        Object data = nodesRaw.get("data");
        if (data instanceof Map) {
            Object itemsObj = ((Map) data).get("items");
            if (itemsObj instanceof List) {
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
                    resources.add(r);
                }
            }
        }

        Map<String, Object> cluster = new LinkedHashMap<>();
        cluster.put("status",      connected ? "Connected" : "Disconnected");
        cluster.put("live",        connected);
        cluster.put("generatedAt", Instant.now().toString());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cluster",   cluster);
        out.put("resources", resources);
        out.put("logs",      Collections.emptyList());
        out.put("live",      connected);
        return out;
    }

    /** Angular expects: List<{time, service, level, message}> */
    @GetMapping("/logs")
    public List<Map<String, Object>> logs(@RequestParam(required = false) String namespace) {
        Map raw  = service.events(namespace);
        boolean live = Boolean.TRUE.equals(raw.get("live"));
        List<Map<String, Object>> result = new ArrayList<>();
        Object data = raw.get("data");
        if (data instanceof Map) {
            Object items = ((Map) data).get("items");
            if (items instanceof List) {
                for (Object ev : (List) items) {
                    if (!(ev instanceof Map)) continue;
                    Map event  = (Map) ev;
                    Map involv = asMap(event.get("involvedObject"));
                    String type    = str(event.getOrDefault("type", "Normal"));
                    String reason  = str(event.getOrDefault("reason", ""));
                    String message = str(event.getOrDefault("message", ""));
                    Map<String, Object> log = new LinkedHashMap<>();
                    log.put("time",    str(event.getOrDefault("lastTimestamp",
                                          event.getOrDefault("firstTimestamp", Instant.now().toString()))));
                    log.put("service", str(involv.getOrDefault("name", "kubernetes")));
                    log.put("level",   "Warning".equals(type) ? "WARN" : "INFO");
                    log.put("message", reason.isBlank() ? message : reason + ": " + message);
                    result.add(log);
                }
            }
        }
        if (!live && result.isEmpty()) {
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("time",    Instant.now().toString());
            w.put("service", "gateway");
            w.put("level",   "WARN");
            w.put("message", "kubectl unavailable — no cluster configured");
            result.add(w);
        }
        return result;
    }

    @GetMapping("/health")
    public Map<String, Object> health() { return service.nodes(); }

    @GetMapping("/resources")
    public Map<String, Object> list(@RequestParam(defaultValue = "pods") String kind,
                                    @RequestParam(required = false) String namespace) {
        return service.resource(kind, namespace);
    }

    @GetMapping("/resources/{resourceId}")
    public Map<String, Object> get(@PathVariable String resourceId) {
        return service.resource(resourceId, null);
    }

    @GetMapping("/nodes")
    public Map<String, Object> nodes() { return service.nodes(); }

    @GetMapping("/namespaces")
    public Map<String, Object> namespaces() { return service.namespaces(); }

    @GetMapping("/{kind}")
    public Map<String, Object> resource(@PathVariable String kind,
                                        @RequestParam(required = false) String namespace) {
        return service.resource(kind, namespace);
    }

    private Map asMap(Object o) { return o instanceof Map ? (Map) o : Collections.emptyMap(); }
    private String str(Object o) { return o == null ? "" : String.valueOf(o); }
}
