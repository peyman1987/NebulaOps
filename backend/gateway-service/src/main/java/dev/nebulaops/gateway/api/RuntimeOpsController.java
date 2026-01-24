package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * v22.4 — Runtime endpoints.
 * Normalizes Docker Engine API fields so Angular normalizeDockerContainer() works:
 *   Docker API: Id (64-char), Names (["/name"]), Image, State, Ports ([{PublicPort,PrivatePort}])
 *   Angular expects: id, name, image, status, ports (string)
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

    @GetMapping("/docker/containers")
    public List<Map<String,Object>> containers() {
        List<Map<String,Object>> result = new ArrayList<>();
        for (Object o : items(docker.containers())) {
            if (o instanceof Map) result.add(normalizeContainer((Map) o));
        }
        return result;
    }

    @GetMapping("/docker/images")
    public List<Map<String,Object>> images() {
        List<Map<String,Object>> result = new ArrayList<>();
        for (Object o : items(docker.images())) {
            if (o instanceof Map) result.add(normalizeImage((Map) o));
        }
        return result;
    }

    @GetMapping("/docker/volumes")
    public List<Map<String,Object>> volumes() {
        List<Map<String,Object>> result = new ArrayList<>();
        for (Object o : items(docker.volumes())) {
            if (o instanceof Map) result.add(normalizeVolume((Map) o));
        }
        return result;
    }

    @GetMapping("/docker/stats")   public List stats()    { return List.of(); }
    @GetMapping("/docker/networks")public List networks() { return items(docker.networks()); }
    @GetMapping("/docker/builds")  public List builds()   { return List.of(); }

    @GetMapping("/helm/releases")
    public List helm(@RequestParam(required = false) String namespace) {
        Map raw = kubernetes.helmReleases(namespace);
        Object data = raw.get("data");
        return data instanceof List ? (List) data : Collections.emptyList();
    }

    @GetMapping("/grafana/health")
    public Map<String,Object> grafana() { return observability.grafanaHealth(); }

    @GetMapping("/terraform/validate")
    public Map<String,Object> terraformValidate() { return terraform.modules(null); }

    @GetMapping("/terraform/plan")
    public Map<String,Object> terraformPlan(@RequestParam(required=false) String workspace) {
        return terraform.plan(workspace);
    }

    // ── Docker Engine API → Angular-compatible shapes ─────────────────────────

    /**
     * Docker Engine API /containers/json fields:
     *   Id          : full 64-char hex ID
     *   Names       : ["/container-name"] (array with leading slash)
     *   Image       : image name+tag
     *   State       : "running" | "paused" | "restarting" | "exited" | "dead"
     *   Status      : human-readable e.g. "Up 2 hours"
     *   Ports       : [{IP, PrivatePort, PublicPort, Type}]
     *   NetworkSettings.Networks : {networkName: {IPAddress,...}}
     *
     * Angular normalizeDockerContainer() reads: id, name, image, status, ports
     */
    private Map<String,Object> normalizeContainer(Map raw) {
        // ID: use full Id for action calls (Docker API accepts it)
        String id = str(raw.getOrDefault("Id", raw.getOrDefault("ID", "")));

        // Name: strip leading slash from first element of Names array
        String name = "";
        Object names = raw.get("Names");
        if (names instanceof List && !((List)names).isEmpty()) {
            name = String.valueOf(((List)names).get(0)).replaceFirst("^/+","");
        } else {
            name = str(raw.getOrDefault("Name", id.length() >= 12 ? id.substring(0,12) : id));
        }

        // State: Docker Engine uses lowercase — Angular normalizeDockerContainer handles it
        String state  = str(raw.getOrDefault("State",  "unknown")).toLowerCase();
        String status = str(raw.getOrDefault("Status", state));

        // Ports: build "host:container" string
        String ports = "";
        Object portsObj = raw.get("Ports");
        if (portsObj instanceof List) {
            StringBuilder pb = new StringBuilder();
            for (Object p : (List)portsObj) {
                if (p instanceof Map) {
                    Object pub  = ((Map)p).get("PublicPort");
                    Object priv = ((Map)p).get("PrivatePort");
                    if (pub != null && !String.valueOf(pub).equals("0")) {
                        pb.append(pub).append(":").append(priv).append(" ");
                    }
                }
            }
            ports = pb.toString().trim();
        }

        // Network
        String network = "";
        try {
            Object ns = raw.get("NetworkSettings");
            if (ns instanceof Map) {
                Object nets = ((Map)ns).get("Networks");
                if (nets instanceof Map && !((Map)nets).isEmpty()) {
                    network = String.join(", ", ((Map)nets).keySet().stream().map(Object::toString).toList());
                }
            }
        } catch (Exception ignored) {}

        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id",      id);
        out.put("name",    name);
        out.put("image",   str(raw.getOrDefault("Image", "-")));
        out.put("status",  state);    // Angular normalizeDockerContainer maps state to status
        out.put("cpu",     0);
        out.put("memory",  0);
        out.put("ports",   ports.isEmpty() ? "-" : ports);
        out.put("network", network.isEmpty() ? "-" : network);
        out.put("logs",    List.of(name + " — " + status));
        // Keep raw Docker API fields too — Angular reads x.Status for display
        out.put("Status",  status);
        out.put("State",   state);
        return out;
    }

    /**
     * Docker Engine API /images/json fields:
     *   Id, RepoTags ([repo:tag]), Size (bytes), Created (epoch seconds)
     */
    private Map<String,Object> normalizeImage(Map raw) {
        String repoTag = "<none>:<none>";
        Object tags = raw.get("RepoTags");
        if (tags instanceof List && !((List)tags).isEmpty()) {
            String first = String.valueOf(((List)tags).get(0));
            if (!first.contains("<none>")) repoTag = first;
        }
        String[] parts = repoTag.contains(":") ? repoTag.split(":",2) : new String[]{repoTag,"latest"};
        long sizeBytes = toLong(raw.getOrDefault("Size", 0));
        String sizeStr = sizeBytes > 1_000_000_000
                ? String.format("%.1fGB", sizeBytes/1e9)
                : String.format("%.0fMB", sizeBytes/1e6);
        long epoch = toLong(raw.getOrDefault("Created", 0));
        String created = epoch > 0 ? epochAgo(epoch) : "-";
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id",              str(raw.getOrDefault("Id","")));
        out.put("repository",      parts[0]);
        out.put("tag",             parts[1]);
        out.put("size",            sizeStr);
        out.put("vulnerabilities", 0);
        out.put("created",         created);
        return out;
    }

    /** Docker Engine API /volumes → Volumes[{Name,Driver,Mountpoint,Scope}] */
    private Map<String,Object> normalizeVolume(Map raw) {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("name",   str(raw.getOrDefault("Name",   "-")));
        out.put("driver", str(raw.getOrDefault("Driver", "local")));
        out.put("mount",  str(raw.getOrDefault("Mountpoint", "-")));
        out.put("size",   str(raw.getOrDefault("Scope",  "-")));
        return out;
    }

    private List items(Map raw) {
        Object i = raw.get("items");
        if (i instanceof List) return (List)i;
        Object d = raw.get("data");
        if (d instanceof List) return (List)d;
        return Collections.emptyList();
    }

    private String str(Object o)   { return o == null ? "" : String.valueOf(o); }
    private long   toLong(Object o){ try{ return Long.parseLong(String.valueOf(o)); } catch(Exception e){ return 0; } }

    private String epochAgo(long epoch) {
        long diff = System.currentTimeMillis()/1000 - epoch;
        if (diff < 3600)  return (diff/60) + " min ago";
        if (diff < 86400) return (diff/3600) + " hours ago";
        return (diff/86400) + " days ago";
    }
}
