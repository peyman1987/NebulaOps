package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * v21.2-fixed — Runtime endpoints returning shapes Angular expects (plain Lists).
 *
 * Docker data now comes from DockerSocketClient (Unix socket) so field names
 * are Docker Engine API names (Id, Names, Image, State, Ports, etc.).
 * normalizeContainer/Image/Volume map them to the shape Angular expects.
 */
@SuppressWarnings({"unchecked","rawtypes"})
@RestController
@RequestMapping("/api/runtime")
public class RuntimeOpsController {

    private final DockerRuntimeService     docker;
    private final KubernetesPlatformService kubernetes;
    private final ObservabilityPlatformService observability;
    private final TerraformPlatformService terraform;

    public RuntimeOpsController(DockerRuntimeService docker,
                                KubernetesPlatformService kubernetes,
                                ObservabilityPlatformService observability,
                                TerraformPlatformService terraform) {
        this.docker      = docker;
        this.kubernetes  = kubernetes;
        this.observability = observability;
        this.terraform   = terraform;
    }

    @GetMapping("/docker/containers")
    public List<Map<String, Object>> containers() {
        List raw = items(docker.containers());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object o : raw) {
            if (o instanceof Map) result.add(normalizeContainer((Map) o));
        }
        return result;
    }

    @GetMapping("/docker/images")
    public List<Map<String, Object>> images() {
        List raw = items(docker.images());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object o : raw) {
            if (o instanceof Map) result.add(normalizeImage((Map) o));
        }
        return result;
    }

    @GetMapping("/docker/volumes")
    public List<Map<String, Object>> volumes() {
        List raw = items(docker.volumes());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object o : raw) {
            if (o instanceof Map) result.add(normalizeVolume((Map) o));
        }
        return result;
    }

    @GetMapping("/docker/stats")
    public List stats()    { return items(docker.stats()); }

    @GetMapping("/docker/networks")
    public List networks() { return items(docker.networks()); }

    @GetMapping("/docker/builds")
    public List builds()   { return items(docker.builds()); }

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

    // ── Docker Engine API → Angular shape ─────────────────────────────────────

    /**
     * Docker Engine API /containers/json returns:
     *   Id, Names (array), Image, ImageID, State, Status, Ports (array), ...
     * Angular normalizeDockerContainer expects:
     *   id, name, image, status, cpu, memory, ports, network, logs
     */
    private Map<String, Object> normalizeContainer(Map raw) {
        // Names is a list like ["/gateway-service"] — strip leading slash
        String name = "";
        Object names = raw.get("Names");
        if (names instanceof List && !((List) names).isEmpty()) {
            name = String.valueOf(((List) names).get(0)).replaceFirst("^/", "");
        } else {
            name = str(raw.getOrDefault("Name", raw.getOrDefault("Id", "container")));
        }

        // Ports: [{IP, PrivatePort, PublicPort, Type}]
        String ports = "";
        Object portsObj = raw.get("Ports");
        if (portsObj instanceof List) {
            StringBuilder pb = new StringBuilder();
            for (Object p : (List) portsObj) {
                if (p instanceof Map) {
                    Map pm = (Map) p;
                    Object pub = pm.get("PublicPort");
                    Object priv = pm.get("PrivatePort");
                    if (pub != null) pb.append(pub).append(":").append(priv).append(" ");
                }
            }
            ports = pb.toString().trim();
        }

        String state  = str(raw.getOrDefault("State",  "unknown"));
        String status = str(raw.getOrDefault("Status", state));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id",      str(raw.getOrDefault("Id", "")));
        out.put("name",    name);
        out.put("image",   str(raw.getOrDefault("Image", "-")));
        out.put("status",  state.equals("running") ? "running"
                         : state.equals("paused")  ? "paused"
                         : state.equals("restarting") ? "restarting" : "stopped");
        out.put("cpu",     0);   // real CPU needs /stats streaming endpoint
        out.put("memory",  0);
        out.put("ports",   ports.isEmpty() ? "-" : ports);
        out.put("network", networksOf(raw));
        out.put("logs",    List.of(name + " " + status));
        out.put("State",   state);
        out.put("Status",  status);
        return out;
    }

    private String networksOf(Map raw) {
        try {
            Object net = raw.get("NetworkSettings");
            if (net instanceof Map) {
                Object nets = ((Map) net).get("Networks");
                if (nets instanceof Map && !((Map) nets).isEmpty()) {
                    return String.join(", ", ((Map) nets).keySet().stream()
                        .map(Object::toString).toList());
                }
            }
        } catch (Exception ignored) {}
        return "-";
    }

    /**
     * Docker Engine API /images/json returns:
     *   Id, RepoTags (array), Size, Created (epoch)
     */
    private Map<String, Object> normalizeImage(Map raw) {
        String repoTag = "<none>:<none>";
        Object tags = raw.get("RepoTags");
        if (tags instanceof List && !((List) tags).isEmpty()) {
            repoTag = String.valueOf(((List) tags).get(0));
        }
        String[] parts = repoTag.contains(":") ? repoTag.split(":", 2) : new String[]{repoTag, "latest"};

        long sizeBytes = toLong(raw.getOrDefault("Size", 0));
        String sizeStr = sizeBytes > 1_000_000_000
            ? String.format("%.1fGB", sizeBytes / 1e9)
            : String.format("%.0fMB", sizeBytes / 1e6);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("repository",      parts[0]);
        out.put("tag",             parts[1]);
        out.put("size",            sizeStr);
        out.put("vulnerabilities", 0);
        out.put("created",         epochToAgo(toLong(raw.getOrDefault("Created", 0))));
        return out;
    }

    /**
     * Docker Engine API /volumes returns Volumes array with:
     *   Name, Driver, Mountpoint, Scope
     */
    private Map<String, Object> normalizeVolume(Map raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name",   str(raw.getOrDefault("Name",       "-")));
        out.put("driver", str(raw.getOrDefault("Driver",     "local")));
        out.put("mount",  str(raw.getOrDefault("Mountpoint", "-")));
        out.put("size",   str(raw.getOrDefault("Scope",      "-")));
        return out;
    }

    private String epochToAgo(long epoch) {
        if (epoch == 0) return "-";
        long diffSec = System.currentTimeMillis() / 1000 - epoch;
        if (diffSec < 3600)   return (diffSec / 60) + " min ago";
        if (diffSec < 86400)  return (diffSec / 3600) + " hours ago";
        return (diffSec / 86400) + " days ago";
    }

    private String str(Object o)  { return o == null ? "" : String.valueOf(o); }
    private long   toLong(Object o) {
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    private List items(Map raw) {
        Object i = raw.get("items");
        if (i instanceof List) return (List) i;
        Object d = raw.get("data");
        if (d instanceof List) return (List) d;
        return Collections.emptyList();
    }
}
