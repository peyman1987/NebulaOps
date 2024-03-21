package dev.nebulaops.gateway.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/kubernetes")
public class KubernetesOpsController {
    private static final List<String> LIST_KINDS = List.of("namespaces", "deployments", "replicasets", "statefulsets", "daemonsets", "services", "ingresses", "configmaps", "secrets", "cronjobs");
    private final ObjectMapper mapper;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public KubernetesOpsController(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private static void flatten(List<String> command, Object[] args) {
        for (Object arg : args) {
            if (arg == null) continue;
            if (arg instanceof String s) {
                if (!s.isBlank()) command.add(s);
            } else if (arg instanceof String[] arr) command.addAll(Arrays.asList(arr));
            else if (arg instanceof Collection<?> col) col.forEach(x -> command.add(String.valueOf(x)));
            else command.add(String.valueOf(arg));
        }
    }

    private static String[] namespaceArgs(String kind, String namespace) {
        if ("Namespace".equalsIgnoreCase(kind) || namespace == null || namespace.isBlank()) return new String[]{};
        return new String[]{"-n", namespace};
    }

    private static String id(String kind, String namespace, String name) {
        return kind + ":" + namespace + ":" + name;
    }

    private static Identity parseId(String id) {
        String[] p = java.net.URLDecoder.decode(id, StandardCharsets.UTF_8).split(":", 3);
        if (p.length != 3) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid resource id");
        return new Identity(p[0], p[1], p[2]);
    }

    private static String clean(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String toKind(String kubectlKind) {
        return switch (kubectlKind) {
            case "namespaces" -> "Namespace";
            case "deployments" -> "Deployment";
            case "replicasets" -> "ReplicaSet";
            case "statefulsets" -> "StatefulSet";
            case "daemonsets" -> "DaemonSet";
            case "services" -> "Service";
            case "ingresses" -> "Ingress";
            case "configmaps" -> "ConfigMap";
            case "secrets" -> "Secret";
            case "cronjobs" -> "CronJob";
            default -> kubectlKind;
        };
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String state(String kind, Map<String, Object> status) {
        if ("Namespace".equals(kind)) return Objects.toString(status.getOrDefault("phase", "Active"));
        if (status.containsKey("availableReplicas"))
            return intValue(status.get("availableReplicas"), 0) > 0 ? "Available" : "Unavailable";
        if (status.containsKey("loadBalancer")) return "Ready";
        return "Ready";
    }

    private static String serviceFromLogLine(String line) {
        int slash = line.indexOf('/');
        if (slash > 0) return line.substring(0, slash).replace("pod/", "");
        return "kubernetes";
    }

    private static String levelFrom(String line) {
        String u = line.toUpperCase(Locale.ROOT);
        if (u.contains("ERROR")) return "ERROR";
        if (u.contains("WARN")) return "WARN";
        return "INFO";
    }

    private static String yaml(String kind, String namespace, String name, int replicas) {
        if ("Namespace".equals(kind)) return "apiVersion: v1\nkind: Namespace\nmetadata:\n  name: " + name + "\n";
        if ("Deployment".equals(kind) || "ReplicaSet".equals(kind) || "StatefulSet".equals(kind) || "DaemonSet".equals(kind))
            return "apiVersion: apps/v1\nkind: " + kind + "\nmetadata:\n  name: " + name + "\n  namespace: " + namespace + "\nspec:\n  replicas: " + replicas + "\n  selector:\n    matchLabels:\n      app: " + name + "\n  template:\n    metadata:\n      labels:\n        app: " + name + "\n    spec:\n      containers:\n        - name: " + name + "\n          image: nginx:1.27-alpine\n          ports:\n            - containerPort: 80\n";
        if ("Service".equals(kind))
            return "apiVersion: v1\nkind: Service\nmetadata:\n  name: " + name + "\n  namespace: " + namespace + "\nspec:\n  type: ClusterIP\n  selector:\n    app: " + name + "\n  ports:\n    - port: 80\n      targetPort: 80\n";
        if ("Ingress".equals(kind))
            return "apiVersion: networking.k8s.io/v1\nkind: Ingress\nmetadata:\n  name: " + name + "\n  namespace: " + namespace + "\nspec:\n  rules:\n    - host: " + name + ".local\n      http:\n        paths:\n          - path: /\n            pathType: Prefix\n            backend:\n              service:\n                name: " + name + "\n                port:\n                  number: 80\n";
        return "apiVersion: v1\nkind: " + kind + "\nmetadata:\n  name: " + name + "\n  namespace: " + namespace + "\n";
    }

    @GetMapping("/snapshot")
    public Snapshot snapshot() {
        var resources = listResources(null, null);
        return new Snapshot(clusterInfo(), resources, logs());
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return clusterInfo();
    }

    @GetMapping("/logs")
    public List<Map<String, String>> microserviceLogs() {
        return logs();
    }

    @GetMapping("/resources")
    public List<Resource> list(@RequestParam(required = false) String kind, @RequestParam(required = false) String namespace) {
        return listResources(kind, namespace);
    }

    @GetMapping("/resources/{resourceId}")
    public Resource get(@PathVariable String resourceId) {
        Identity id = parseId(resourceId);
        return readOne(id.kind(), id.namespace(), id.name());
    }

    @PostMapping("/resources")
    public Resource create(@RequestBody ResourceRequest req) {
        String yaml = req.yaml();
        if (yaml == null || yaml.isBlank()) {
            yaml = yaml(req.kind() == null ? "Deployment" : req.kind(), clean(req.namespace(), "default"), clean(req.name(), "nebula-resource"), req.replicas() == null ? 1 : req.replicas());
        }
        return applyYaml(new YamlRequest(yaml));
    }

    @PutMapping("/resources/{resourceId}")
    public Resource update(@PathVariable String resourceId, @RequestBody ResourceRequest req) {
        if (req.yaml() == null || req.yaml().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "yaml is required for update");
        }
        return applyYaml(new YamlRequest(req.yaml()));
    }

    @PostMapping("/yaml/apply")
    public Resource applyYaml(@RequestBody YamlRequest req) {
        if (req.yaml() == null || req.yaml().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "yaml is required");
        }
        runWithInput(req.yaml(), "apply", "-f", "-");
        var meta = readYamlIdentity(req.yaml());
        return readOne(meta.kind(), meta.namespace(), meta.name());
    }

    @PatchMapping("/resources/{resourceId}/scale")
    public Resource scale(@PathVariable String resourceId, @RequestBody ScaleRequest req) {
        Identity id = parseId(resourceId);
        if (!Set.of("Deployment", "ReplicaSet", "StatefulSet").contains(id.kind())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only scalable workload resources can be scaled");
        }
        run(null, "scale", id.kind().toLowerCase(Locale.ROOT) + "/" + id.name(), "--replicas=" + Math.max(0, req.replicas()), namespaceArgs(id.kind(), id.namespace()));
        return readOne(id.kind(), id.namespace(), id.name());
    }

    @DeleteMapping("/resources/{resourceId}")
    public Map<String, Object> delete(@PathVariable String resourceId) {
        Identity id = parseId(resourceId);
        run(null, "delete", id.kind(), id.name(), namespaceArgs(id.kind(), id.namespace()), "--ignore-not-found=true");
        return Map.of("id", resourceId, "deleted", true, "appliedAt", Instant.now().toString());
    }

    private List<Resource> listResources(String requestedKind, String requestedNamespace) {
        ensureKubectlAvailable();
        List<Resource> out = new ArrayList<>();
        for (String kubectlKind : LIST_KINDS) {
            String normalizedKind = toKind(kubectlKind);
            if (requestedKind != null && !"all".equalsIgnoreCase(requestedKind) && !normalizedKind.equalsIgnoreCase(requestedKind))
                continue;
            try {
                String json = kubectlKind.equals("namespaces") ? run(null, "get", kubectlKind, "-o", "json") : run(null, "get", kubectlKind, "-A", "-o", "json");
                Map<String, Object> root = mapper.readValue(json, new TypeReference<>() {
                });
                List<Map<String, Object>> items = (List<Map<String, Object>>) root.getOrDefault("items", List.of());
                for (Map<String, Object> item : items) {
                    Resource r = toResource(item, normalizedKind, false);
                    if (requestedNamespace == null || "all".equalsIgnoreCase(requestedNamespace) || requestedNamespace.equals(r.namespace()))
                        out.add(r);
                }
            } catch (Exception ignored) {
                // Some clusters do not enable every resource type. Continue with available kinds.
            }
        }
        out.sort(Comparator.comparing(Resource::kind).thenComparing(Resource::namespace).thenComparing(Resource::name));
        return out;
    }

    private Resource readOne(String kind, String namespace, String name) {
        String yaml = run(null, "get", kind, name, namespaceArgs(kind, namespace), "-o", "yaml");
        String json = run(null, "get", kind, name, namespaceArgs(kind, namespace), "-o", "json");
        try {
            Map<String, Object> item = mapper.readValue(json, new TypeReference<>() {
            });
            Resource r = toResource(item, kind, true);
            return new Resource(r.id(), r.kind(), r.namespace(), r.name(), r.replicas(), r.status(), yaml, r.updatedAt());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to parse kubectl output: " + e.getMessage());
        }
    }

    private Resource toResource(Map<String, Object> item, String fallbackKind, boolean includeYaml) {
        Map<String, Object> metadata = (Map<String, Object>) item.getOrDefault("metadata", Map.of());
        Map<String, Object> spec = (Map<String, Object>) item.getOrDefault("spec", Map.of());
        Map<String, Object> status = (Map<String, Object>) item.getOrDefault("status", Map.of());
        String kind = Objects.toString(item.getOrDefault("kind", fallbackKind));
        String name = Objects.toString(metadata.getOrDefault("name", "unknown"));
        String namespace = "Namespace".equals(kind) ? name : Objects.toString(metadata.getOrDefault("namespace", "default"));
        int replicas = intValue(spec.get("replicas"), intValue(status.get("replicas"), 0));
        String state = state(kind, status);
        String updatedAt = Objects.toString(metadata.getOrDefault("creationTimestamp", Instant.now().toString()));
        return new Resource(id(kind, namespace, name), kind, namespace, name, replicas, state, includeYaml ? "" : "", updatedAt);
    }

    private Map<String, Object> clusterInfo() {
        try {
            String context = run(null, "config", "current-context").trim();
            String version = run(null, "version", "-o", "json").replace('\n', ' ').trim();
            return Map.of("name", context, "provider", "kubectl", "version", version, "status", "Connected");
        } catch (Exception e) {
            return Map.of("name", "not-connected", "provider", "kubectl", "version", "unknown", "status", "Kubeconfig unavailable: " + e.getMessage());
        }
    }

    private List<Map<String, String>> logs() {
        List<Map<String, String>> dockerRows = dockerComposeLogs();
        if (!dockerRows.isEmpty()) return dockerRows;
        try {
            String raw = run(null, "logs", "-n", "nebulaops", "--all-containers=true", "--tail=60", "-l", "app.kubernetes.io/part-of=nebulaops", "--prefix=true");
            List<Map<String, String>> rows = new ArrayList<>();
            for (String line : raw.split("\\R")) {
                if (line.isBlank()) continue;
                rows.add(Map.of("time", Instant.now().toString(), "service", serviceFromLogLine(line), "level", levelFrom(line), "message", line));
            }
            return rows;
        } catch (Exception e) {
            return List.of(Map.of("time", Instant.now().toString(), "service", "gateway-service", "level", "WARN", "message", "Logs unavailable. Mount /var/run/docker.sock for Docker logs or deploy labelled Kubernetes pods. " + e.getMessage()));
        }
    }

    private List<Map<String, String>> dockerComposeLogs() {
        try {
            File socket = new File("/var/run/docker.sock");
            if (!socket.exists()) return List.of();
            String project = System.getenv().getOrDefault("DOCKER_PROJECT_NAME", "nebulaops-v14");
            String containersJson = command("curl", "-fsS", "--unix-socket", "/var/run/docker.sock", "http://localhost/containers/json?all=0");
            List<Map<String, Object>> containers = mapper.readValue(containersJson, new TypeReference<>() {
            });
            List<Map<String, String>> rows = new ArrayList<>();
            Set<String> wanted = Set.of("gateway-service", "auth-service", "task-service", "file-service", "notification-service", "go-cache-service", "go-event-worker", "frontend");
            for (Map<String, Object> c : containers) {
                String id = Objects.toString(c.get("Id"), "");
                List<String> names = (List<String>) c.getOrDefault("Names", List.of());
                String rawName = names.isEmpty() ? id : names.get(0).replaceFirst("^/", "");
                Map<String, Object> labels = (Map<String, Object>) c.getOrDefault("Labels", Map.of());
                String service = Objects.toString(labels.getOrDefault("com.docker.compose.service", rawName));
                String composeProject = Objects.toString(labels.getOrDefault("com.docker.compose.project", ""));
                if (!wanted.contains(service)) continue;
                if (!composeProject.isBlank() && !composeProject.equals(project)) continue;
                String url = "http://localhost/containers/" + id + "/logs?stdout=1&stderr=1&tail=18&timestamps=1";
                String raw = command("curl", "-fsS", "--unix-socket", "/var/run/docker.sock", url);
                for (String line : raw.split("\\R")) {
                    String cleaned = line.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "").trim();
                    if (cleaned.isBlank()) continue;
                    String time = cleaned.length() > 20 && cleaned.charAt(10) == 'T' ? cleaned.substring(0, Math.min(30, cleaned.length())) : Instant.now().toString();
                    rows.add(Map.of("time", time, "service", service, "level", levelFrom(cleaned), "message", cleaned));
                }
            }
            rows.sort(Comparator.comparing(m -> m.getOrDefault("time", "")));
            if (rows.size() > 160) return rows.subList(Math.max(0, rows.size() - 160), rows.size());
            return rows;
        } catch (Exception e) {
            return List.of(Map.of("time", Instant.now().toString(), "service", "gateway-service", "level", "WARN", "message", "Docker logs unavailable: " + e.getMessage()));
        }
    }

    private String command(String... args) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean completed = p.waitFor(12, TimeUnit.SECONDS);
        if (!completed) {
            p.destroyForcibly();
            throw new IOException("command timed out: " + String.join(" ", args));
        }
        if (p.exitValue() != 0) throw new IOException(output);
        return output;
    }

    private void ensureKubectlAvailable() {
        try {
            run(null, "version", "--client=true", "-o", "json");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "kubectl is not available or kubeconfig is not mounted. " + e.getMessage());
        }
    }

    private String run(String input, Object... args) {
        List<String> command = new ArrayList<>();
        command.add("kubectl");
        flatten(command, args);
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().putIfAbsent("KUBECONFIG", System.getenv().getOrDefault("KUBECONFIG", "/kube/config"));
            Process p = pb.start();
            if (input != null) {
                try (OutputStream os = p.getOutputStream()) {
                    os.write(input.getBytes(StandardCharsets.UTF_8));
                }
            }
            String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean completed = p.waitFor(30, TimeUnit.SECONDS);
            if (!completed) {
                p.destroyForcibly();
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "kubectl command timed out");
            }
            if (p.exitValue() != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, stderr.isBlank() ? stdout : stderr);
            }
            return stdout;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "kubectl execution failed: " + e.getMessage());
        }
    }

    private String runWithInput(String input, Object... args) {
        return run(input, args);
    }

    private Identity readYamlIdentity(String yaml) {
        try {
            Map<String, Object> m = yamlMapper.readValue(yaml, new TypeReference<>() {
            });
            Map<String, Object> meta = (Map<String, Object>) m.getOrDefault("metadata", Map.of());
            String kind = Objects.toString(m.get("kind"), "Deployment");
            String name = Objects.toString(meta.get("name"), "");
            String namespace = "Namespace".equals(kind) ? name : Objects.toString(meta.getOrDefault("namespace", "default"));
            if (name.isBlank()) throw new IllegalArgumentException("metadata.name is required");
            return new Identity(kind, namespace, name);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid YAML identity. Required: kind, metadata.name, optional metadata.namespace. " + e.getMessage());
        }
    }

    @GetMapping("/namespaces")
    public ResponseEntity<String> namespaces() {
        return run("kubectl get namespaces -o json");
    }

    @GetMapping("/pods")
    public ResponseEntity<String> pods(@RequestParam(defaultValue = "default") String namespace) {
        return run("kubectl get pods -n " + namespace + " -o json");
    }

    @GetMapping("/deployments")
    public ResponseEntity<String> deployments(@RequestParam(defaultValue = "default") String namespace) {
        return run("kubectl get deployments -n " + namespace + " -o json");
    }

    @GetMapping("/services")
    public ResponseEntity<String> services(@RequestParam(defaultValue = "default") String namespace) {
        return run("kubectl get services -n " + namespace + " -o json");
    }

    @GetMapping("/ingresses")
    public ResponseEntity<String> ingresses(@RequestParam(defaultValue = "default") String namespace) {
        return run("kubectl get ingress -n " + namespace + " -o json");
    }

    private ResponseEntity<String> run(String command) {
        try {
            Process process = new ProcessBuilder("bash", "-lc", command)
                    .redirectErrorStream(true)
                    .start();

            String output = new BufferedReader(new InputStreamReader(process.getInputStream()))
                    .lines()
                    .collect(Collectors.joining("\n"));

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return ResponseEntity.ok(output);
            }

            return ResponseEntity.status(500).body(output);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    public record Snapshot(Map<String, Object> cluster, List<Resource> resources, List<Map<String, String>> logs) {
    }

    public record Resource(String id, String kind, String namespace, String name, int replicas, String status,
                           String yaml, String updatedAt) {
    }

    public record ResourceRequest(String kind, String namespace, String name, Integer replicas, String yaml) {
    }

    public record YamlRequest(String yaml) {
    }

    public record ScaleRequest(int replicas) {
    }

    private record Identity(String kind, String namespace, String name) {
    }
}
