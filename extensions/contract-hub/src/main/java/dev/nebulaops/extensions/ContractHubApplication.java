package dev.nebulaops.extensions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootApplication
public class ContractHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContractHubApplication.class, args);
    }
}

@Controller
class UiController {
    private static final String SLUG = "contract-hub";
    @Value("${nebulaops.extension.title}") String title;
    @Value("${nebulaops.extension.category}") String category;
    @Value("${nebulaops.extension.node-port}") int nodePort;

    @GetMapping({"/", "/" + SLUG, "/" + SLUG + "/"})
    String index(Model model) {
        model.addAttribute("slug", SLUG);
        model.addAttribute("title", title);
        model.addAttribute("category", category);
        model.addAttribute("nodePort", nodePort);
        return "index";
    }
}

@RestController
class LiveController {
    private static final String SLUG = "contract-hub";
    private final LiveDataService service;

    LiveController(LiveDataService service) {
        this.service = service;
    }

    @GetMapping({"/healthz", "/" + SLUG + "/healthz"})
    Map<String, Object> health() {
        return Map.of("status", "UP", "extension", SLUG, "runtime", "spring-boot-mvc", "timestamp", Instant.now().toString());
    }

    @GetMapping({"/readyz", "/" + SLUG + "/readyz"})
    Map<String, Object> ready() {
        return service.readiness();
    }

    @GetMapping({"/api/capabilities", "/" + SLUG + "/api/capabilities"})
    Map<String, Object> capabilities() {
        return service.capabilities();
    }

    @GetMapping({"/api/live", "/" + SLUG + "/api/live"})
    Map<String, Object> live() {
        return service.collect();
    }

    @PostMapping(value = {"/api/runbook/execute", "/" + SLUG + "/api/runbook/execute"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> executeRunbook(@RequestBody Map<String, String> request) {
        return service.executeRunbook(request.getOrDefault("path", ""));
    }
}

@Service
class LiveDataService {
    private static final String SLUG = "contract-hub";
    private final Environment env;
    private final ObjectMapper mapper;
    private final RealHttpClient http;
    private final KubernetesApiClient k8s;
    @Value("${nebulaops.extension.title}") String title;
    @Value("${nebulaops.extension.category}") String category;
    @Value("${nebulaops.extension.mode}") String mode;
    @Value("${nebulaops.extension.node-port}") int nodePort;

    LiveDataService(Environment env, ObjectMapper mapper) {
        this.env = env;
        this.mapper = mapper;
        this.http = new RealHttpClient(env, mapper);
        this.k8s = new KubernetesApiClient(env, mapper);
    }

    Map<String, Object> readiness() {
        Map<String, Object> out = base();
        out.put("status", configuredForMode() ? "READY" : "NOT_CONFIGURED");
        out.put("mode", mode);
        out.put("integration", integrationState());
        out.put("generatedAt", Instant.now().toString());
        return out;
    }

    Map<String, Object> capabilities() {
        Map<String, Object> out = base();
        out.put("mode", mode);
        out.put("capabilities", List.of(
                "Live data only",
                "Explicit NOT_CONFIGURED/UNAVAILABLE states",
                "Spring Boot MVC runtime",
                "Kubernetes-ready health and readiness endpoints"
        ));
        out.put("endpoints", List.of(
                "/healthz",
                "/readyz",
                "/api/live",
                "/api/capabilities"
        ));
        out.put("requiredConfiguration", requiredConfiguration());
        out.put("integration", integrationState());
        out.put("generatedAt", Instant.now().toString());
        return out;
    }

    Map<String, Object> collect() {
        Map<String, Object> out = base();
        try {
            Map<String, Object> live = switch (mode) {
                case "kubernetes" -> collectKubernetesWorkloads();
                case "kubernetesConfig" -> collectKubernetesConfig();
                case "kubernetesBackup" -> collectKubernetesBackup();
                case "runbook" -> collectRunbooks();
                case "registry" -> collectRegistry();
                case "slo" -> collectSlo();
                default -> collectHttpTargets(targetEnvForSlug());
            };
            out.putAll(live);
        } catch (Exception e) {
            out.put("status", "UNAVAILABLE");
            out.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            out.put("items", List.of());
        }
        out.put("generatedAt", Instant.now().toString());
        return out;
    }

    Map<String, Object> executeRunbook(String relativePath) {
        Map<String, Object> out = base();
        if (!"runbook".equals(mode)) {
            out.put("status", "NOT_SUPPORTED");
            out.put("message", "Runbook execution is available only from Runbook Center.");
            return out;
        }
        if (!Boolean.parseBoolean(env.getProperty("RUNBOOK_EXECUTION_ENABLED", "false"))) {
            out.put("status", "NOT_CONFIGURED");
            out.put("message", "RUNBOOK_EXECUTION_ENABLED is not true. The extension can list mounted runbooks, but execution is disabled.");
            return out;
        }
        Path root = Path.of(env.getProperty("RUNBOOK_ALLOWED_DIR", "/workspace/scripts")).normalize().toAbsolutePath();
        if (!Files.isDirectory(root)) {
            out.put("status", "NOT_CONFIGURED");
            out.put("message", "RUNBOOK_ALLOWED_DIR does not point to a mounted script directory: " + root);
            return out;
        }
        try {
            Path requested = root.resolve(relativePath).normalize().toAbsolutePath();
            if (!requested.startsWith(root) || !Files.isRegularFile(requested) || !Files.isExecutable(requested)) {
                out.put("status", "REJECTED");
                out.put("message", "Only executable files under RUNBOOK_ALLOWED_DIR can be executed.");
                return out;
            }
            long timeoutSeconds = Long.parseLong(env.getProperty("RUNBOOK_TIMEOUT_SECONDS", "120"));
            Process process = new ProcessBuilder("bash", requested.toString()).redirectErrorStream(true).start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            out.put("status", finished && process.exitValue() == 0 ? "CONNECTED" : "DEGRADED");
            out.put("exitCode", finished ? process.exitValue() : -1);
            out.put("output", limit(output, 12000));
            out.put("message", finished ? "Runbook completed." : "Runbook timeout after " + timeoutSeconds + " seconds.");
            return out;
        } catch (Exception e) {
            out.put("status", "UNAVAILABLE");
            out.put("message", e.getMessage());
            return out;
        }
    }

    private Map<String, Object> base() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("extension", SLUG);
        out.put("title", title);
        out.put("category", category);
        out.put("nodePort", nodePort);
        out.put("runtime", "Spring Boot MVC");
        out.put("dataPolicy", "LIVE_ONLY");
        return out;
    }

    private List<String> requiredConfiguration() {
        return switch (mode) {
            case "kubernetes", "kubernetesConfig", "kubernetesBackup" -> List.of("KUBERNETES_SERVICE_HOST", "Kubernetes service account token", "KUBE_TARGET_NAMESPACE optional");
            case "runbook" -> List.of("RUNBOOK_ALLOWED_DIR", "RUNBOOK_EXECUTION_ENABLED optional", "RUNBOOK_TIMEOUT_SECONDS optional");
            case "registry" -> List.of("EXTENSION_TARGETS");
            case "slo" -> List.of("SLO_FILE optional", "SLO_PROMETHEUS_TARGETS optional");
            default -> List.of(targetEnvForSlug());
        };
    }

    private boolean configuredForMode() {
        return switch (mode) {
            case "kubernetes", "kubernetesConfig", "kubernetesBackup" -> k8s.configured();
            case "runbook" -> Files.isDirectory(Path.of(env.getProperty("RUNBOOK_ALLOWED_DIR", "/workspace/scripts")));
            case "registry" -> !env.getProperty("EXTENSION_TARGETS", "").isBlank();
            case "slo" -> !env.getProperty("SLO_FILE", "").isBlank() || !env.getProperty("SLO_PROMETHEUS_TARGETS", "").isBlank();
            default -> !env.getProperty(targetEnvForSlug(), "").isBlank();
        };
    }

    private Map<String, Object> integrationState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("configured", configuredForMode());
        state.put("required", requiredConfiguration());
        state.put("policy", "No local fallback records are produced when an integration is absent.");
        if (mode.startsWith("kubernetes")) {
            state.put("serviceAccountToken", Files.exists(Path.of(env.getProperty("KUBERNETES_TOKEN_FILE", "/var/run/secrets/kubernetes.io/serviceaccount/token"))));
            state.put("namespace", namespace());
        } else if ("runbook".equals(mode)) {
            Path root = Path.of(env.getProperty("RUNBOOK_ALLOWED_DIR", "/workspace/scripts")).normalize().toAbsolutePath();
            state.put("directory", root.toString());
            state.put("directoryMounted", Files.isDirectory(root));
            state.put("executionEnabled", Boolean.parseBoolean(env.getProperty("RUNBOOK_EXECUTION_ENABLED", "false")));
        } else if ("slo".equals(mode)) {
            String sloFile = env.getProperty("SLO_FILE", "");
            state.put("sloFile", sloFile.isBlank() ? "" : Path.of(sloFile).toString());
            state.put("sloFileMounted", !sloFile.isBlank() && Files.isRegularFile(Path.of(sloFile)));
            state.put("prometheusTargetsConfigured", !env.getProperty("SLO_PROMETHEUS_TARGETS", "").isBlank());
        } else {
            String envName = "registry".equals(mode) ? "EXTENSION_TARGETS" : targetEnvForSlug();
            state.put("targetEnv", envName);
            state.put("targetsConfigured", !env.getProperty(envName, "").isBlank());
        }
        return state;
    }


    private Map<String, Object> collectKubernetesWorkloads() throws Exception {
        if (!k8s.configured()) return notConfigured("Kubernetes service account is not available. Deploy this extension inside Kubernetes.");
        String ns = namespace();
        List<Map<String, Object>> sections = new ArrayList<>();
        sections.add(section("Pods", summarizePods(k8s.getJson("/api/v1/namespaces/" + enc(ns) + "/pods"))));
        sections.add(section("Deployments", summarizeDeployments(k8s.getJson("/apis/apps/v1/namespaces/" + enc(ns) + "/deployments"))));
        sections.add(section("StatefulSets", summarizeStatefulSets(k8s.getJson("/apis/apps/v1/namespaces/" + enc(ns) + "/statefulsets"))));
        sections.add(section("DaemonSets", summarizeDaemonSets(k8s.getJson("/apis/apps/v1/namespaces/" + enc(ns) + "/daemonsets"))));
        sections.add(section("Services", summarizeServices(k8s.getJson("/api/v1/namespaces/" + enc(ns) + "/services"))));
        sections.add(section("Ingresses", summarizeIngresses(k8s.getJson("/apis/networking.k8s.io/v1/namespaces/" + enc(ns) + "/ingresses"))));
        sections.add(section("Events", summarizeEvents(k8s.getJson("/api/v1/namespaces/" + enc(ns) + "/events"))));
        return liveSections(sections, "Live Kubernetes namespace: " + ns);
    }

    private Map<String, Object> collectKubernetesConfig() throws Exception {
        if (!k8s.configured()) return notConfigured("Kubernetes service account is not available. Deploy this extension inside Kubernetes.");
        String ns = namespace();
        List<Map<String, Object>> sections = new ArrayList<>();
        sections.add(section("ConfigMaps", summarizeConfigMaps(k8s.getJson("/api/v1/namespaces/" + enc(ns) + "/configmaps"))));
        sections.add(section("Secrets", summarizeSecrets(k8s.getJson("/api/v1/namespaces/" + enc(ns) + "/secrets"))));
        return liveSections(sections, "Configuration inventory from Kubernetes namespace: " + ns);
    }

    private Map<String, Object> collectKubernetesBackup() throws Exception {
        if (!k8s.configured()) return notConfigured("Kubernetes service account is not available. Deploy this extension inside Kubernetes.");
        String ns = namespace();
        List<Map<String, Object>> sections = new ArrayList<>();
        sections.add(section("CronJobs", summarizeCronJobs(k8s.getJson("/apis/batch/v1/namespaces/" + enc(ns) + "/cronjobs"))));
        sections.add(section("Jobs", summarizeJobs(k8s.getJson("/apis/batch/v1/namespaces/" + enc(ns) + "/jobs"))));
        sections.add(section("PersistentVolumeClaims", summarizePvcs(k8s.getJson("/api/v1/namespaces/" + enc(ns) + "/persistentvolumeclaims"))));
        return liveSections(sections, "Backup-related Kubernetes resources from namespace: " + ns);
    }

    private Map<String, Object> collectRunbooks() throws Exception {
        Path root = Path.of(env.getProperty("RUNBOOK_ALLOWED_DIR", "/workspace/scripts")).normalize().toAbsolutePath();
        if (!Files.isDirectory(root)) return notConfigured("RUNBOOK_ALLOWED_DIR does not point to a mounted script directory: " + root);
        boolean executionEnabled = Boolean.parseBoolean(env.getProperty("RUNBOOK_EXECUTION_ENABLED", "false"));
        List<Map<String, Object>> items;
        try (Stream<Path> walk = Files.walk(root, 4)) {
            items = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".sh"))
                    .sorted()
                    .limit(200)
                    .map(p -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("path", root.relativize(p).toString());
                        m.put("executable", Files.isExecutable(p));
                        m.put("executionEnabled", executionEnabled);
                        try { m.put("modified", Files.getLastModifiedTime(p).toInstant().toString()); } catch (Exception ignored) {}
                        try { m.put("sizeBytes", Files.size(p)); } catch (Exception ignored) {}
                        return m;
                    }).toList();
        }
        Map<String, Object> out = base();
        out.put("status", items.isEmpty() ? "DEGRADED" : "CONNECTED");
        out.put("message", items.isEmpty() ? "No runbook shell scripts found under " + root : "Runbooks discovered under " + root);
        out.put("items", items);
        out.put("sections", List.of(section("Runbooks", items)));
        return out;
    }

    private Map<String, Object> collectRegistry() {
        String targets = env.getProperty("EXTENSION_TARGETS", "");
        if (targets.isBlank()) return notConfigured("EXTENSION_TARGETS is not configured.");
        return collectHttpTargets("EXTENSION_TARGETS");
    }

    private Map<String, Object> collectSlo() throws Exception {
        List<Map<String, Object>> sections = new ArrayList<>();
        String sloFile = env.getProperty("SLO_FILE", "");
        if (!sloFile.isBlank() && Files.isRegularFile(Path.of(sloFile))) {
            String content = Files.readString(Path.of(sloFile));
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("path", sloFile);
            file.put("sizeBytes", content.getBytes(StandardCharsets.UTF_8).length);
            file.put("preview", limit(content, 5000));
            sections.add(section("SLO definitions", List.of(file)));
        }
        String targets = env.getProperty("SLO_PROMETHEUS_TARGETS", "");
        if (!targets.isBlank()) {
            sections.add(section("Prometheus queries", http.probeTargets(targets)));
        }
        if (sections.isEmpty()) return notConfigured("Configure SLO_FILE and/or SLO_PROMETHEUS_TARGETS to load live SLO data.");
        return liveSections(sections, "SLO data loaded from configured sources.");
    }

    private Map<String, Object> collectHttpTargets(String targetEnv) {
        String targets = env.getProperty(targetEnv, "");
        if (targets.isBlank()) return notConfigured(targetEnv + " is not configured.");
        List<Map<String, Object>> items = http.probeTargets(targets);
        Map<String, Object> out = base();
        boolean anyOk = items.stream().anyMatch(i -> String.valueOf(i.get("state")).equals("CONNECTED"));
        boolean anyFailed = items.stream().anyMatch(i -> String.valueOf(i.get("state")).equals("UNAVAILABLE"));
        out.put("status", anyOk ? (anyFailed ? "DEGRADED" : "CONNECTED") : "UNAVAILABLE");
        out.put("message", "Live endpoints checked from " + targetEnv + ".");
        out.put("items", items);
        out.put("sections", List.of(section("Live endpoints", items)));
        return out;
    }

    private String targetEnvForSlug() {
        return SLUG.toUpperCase().replace('-', '_') + "_TARGETS";
    }

    private String namespace() {
        return env.getProperty("KUBE_TARGET_NAMESPACE", env.getProperty("POD_NAMESPACE", readNamespaceFile().orElse("nebulaops")));
    }

    private Optional<String> readNamespaceFile() {
        try {
            Path p = Path.of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");
            return Files.exists(p) ? Optional.of(Files.readString(p).trim()) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Map<String, Object> notConfigured(String message) {
        Map<String, Object> out = base();
        out.put("status", "NOT_CONFIGURED");
        out.put("message", message);
        out.put("items", List.of());
        out.put("sections", List.of());
        return out;
    }

    private Map<String, Object> liveSections(List<Map<String, Object>> sections, String message) {
        int count = sections.stream().mapToInt(s -> ((List<?>) s.getOrDefault("items", List.of())).size()).sum();
        Map<String, Object> out = base();
        out.put("status", count > 0 ? "CONNECTED" : "DEGRADED");
        out.put("message", count > 0 ? message : message + " No matching resources returned by the live API.");
        out.put("sections", sections);
        out.put("items", sections.stream().flatMap(s -> ((List<Map<String, Object>>) s.getOrDefault("items", List.of())).stream()).toList());
        return out;
    }

    private Map<String, Object> section(String name, List<Map<String, Object>> items) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("count", items.size());
        s.put("items", items);
        return s;
    }

    private List<Map<String, Object>> summarizePods(JsonNode root) {
        return list(root).stream().map(n -> {
            Map<String, Object> m = meta(n);
            m.put("phase", path(n, "status", "phase"));
            int ready = 0, total = 0, restarts = 0;
            for (JsonNode c : n.path("status").path("containerStatuses")) {
                total++;
                if (c.path("ready").asBoolean(false)) ready++;
                restarts += c.path("restartCount").asInt(0);
            }
            m.put("ready", ready + "/" + total);
            m.put("restarts", restarts);
            m.put("node", path(n, "spec", "nodeName"));
            return m;
        }).toList();
    }

    private List<Map<String, Object>> summarizeDeployments(JsonNode root) {
        return list(root).stream().map(n -> {
            Map<String, Object> m = meta(n);
            m.put("replicas", n.path("status").path("readyReplicas").asInt(0) + "/" + n.path("spec").path("replicas").asInt(0));
            m.put("available", n.path("status").path("availableReplicas").asInt(0));
            m.put("updated", n.path("status").path("updatedReplicas").asInt(0));
            return m;
        }).toList();
    }

    private List<Map<String, Object>> summarizeStatefulSets(JsonNode root) {
        return list(root).stream().map(n -> {
            Map<String, Object> m = meta(n);
            m.put("replicas", n.path("status").path("readyReplicas").asInt(0) + "/" + n.path("spec").path("replicas").asInt(0));
            m.put("currentReplicas", n.path("status").path("currentReplicas").asInt(0));
            m.put("updatedReplicas", n.path("status").path("updatedReplicas").asInt(0));
            m.put("serviceName", path(n, "spec", "serviceName"));
            return m;
        }).toList();
    }

    private List<Map<String, Object>> summarizeDaemonSets(JsonNode root) {
        return list(root).stream().map(n -> {
            Map<String, Object> m = meta(n);
            m.put("desired", n.path("status").path("desiredNumberScheduled").asInt(0));
            m.put("current", n.path("status").path("currentNumberScheduled").asInt(0));
            m.put("ready", n.path("status").path("numberReady").asInt(0));
            m.put("updated", n.path("status").path("updatedNumberScheduled").asInt(0));
            return m;
        }).toList();
    }

    private List<Map<String, Object>> summarizeIngresses(JsonNode root) {
        return list(root).stream().map(n -> {
            Map<String, Object> m = meta(n);
            m.put("className", path(n, "spec", "ingressClassName"));
            List<String> rules = new ArrayList<>();
            for (JsonNode r : n.path("spec").path("rules")) {
                String host = r.path("host").asText("");
                if (host.isBlank()) host = "*";
                List<String> paths = new ArrayList<>();
                for (JsonNode p : r.path("http").path("paths")) {
                    paths.add(p.path("path").asText("/") + " -> " + p.path("backend").path("service").path("name").asText(""));
                }
                rules.add(host + (paths.isEmpty() ? "" : " " + paths));
            }
            m.put("rules", rules);
            return m;
        }).toList();
    }

    private List<Map<String, Object>> summarizeServices(JsonNode root) {
        return list(root).stream().map(n -> {
            Map<String, Object> m = meta(n);
            m.put("type", path(n, "spec", "type"));
            m.put("clusterIP", path(n, "spec", "clusterIP"));
            List<String> ports = new ArrayList<>();
            for (JsonNode p : n.path("spec").path("ports")) ports.add(p.path("port").asText() + "->" + p.path("targetPort").asText());
            m.put("ports", ports);
            return m;
        }).toList();
    }

    private List<Map<String, Object>> summarizeEvents(JsonNode root) {
        return list(root).stream().sorted(Comparator.comparing(n -> n.path("lastTimestamp").asText(""), Comparator.reverseOrder())).limit(50).map(n -> {
            Map<String, Object> m = meta(n);
            m.put("type", n.path("type").asText(""));
            m.put("reason", n.path("reason").asText(""));
            m.put("object", n.path("involvedObject").path("kind").asText("") + "/" + n.path("involvedObject").path("name").asText(""));
            m.put("message", limit(n.path("message").asText(""), 260));
            m.put("lastTimestamp", n.path("lastTimestamp").asText(""));
            return m;
        }).toList();
    }

    private List<Map<String, Object>> summarizeConfigMaps(JsonNode root) {
        return list(root).stream().map(n -> {
            Map<String, Object> m = meta(n);
            m.put("keys", fieldNames(n.path("data")));
            return m;
        }).toList();
    }

    private List<Map<String, Object>> summarizeSecrets(JsonNode root) {
        return list(root).stream().map(n -> {
            Map<String, Object> m = meta(n);
            m.put("type", n.path("type").asText(""));
            m.put("keys", fieldNames(n.path("data")));
            m.put("values", "MASKED");
            return m;
        }).toList();
    }

    private List<Map<String, Object>> summarizeCronJobs(JsonNode root) {
        return list(root).stream().map(n -> {
            Map<String, Object> m = meta(n);
            m.put("schedule", path(n, "spec", "schedule"));
            m.put("suspend", n.path("spec").path("suspend").asBoolean(false));
            m.put("lastScheduleTime", path(n, "status", "lastScheduleTime"));
            return m;
        }).toList();
    }

    private List<Map<String, Object>> summarizeJobs(JsonNode root) {
        return list(root).stream().sorted(Comparator.comparing(n -> n.path("metadata").path("creationTimestamp").asText(""), Comparator.reverseOrder())).limit(100).map(n -> {
            Map<String, Object> m = meta(n);
            m.put("active", n.path("status").path("active").asInt(0));
            m.put("succeeded", n.path("status").path("succeeded").asInt(0));
            m.put("failed", n.path("status").path("failed").asInt(0));
            m.put("completionTime", path(n, "status", "completionTime"));
            return m;
        }).toList();
    }

    private List<Map<String, Object>> summarizePvcs(JsonNode root) {
        return list(root).stream().map(n -> {
            Map<String, Object> m = meta(n);
            m.put("phase", path(n, "status", "phase"));
            m.put("storageClass", path(n, "spec", "storageClassName"));
            m.put("requested", n.path("spec").path("resources").path("requests").path("storage").asText(""));
            return m;
        }).toList();
    }

    private List<JsonNode> list(JsonNode root) {
        List<JsonNode> out = new ArrayList<>();
        root.path("items").forEach(out::add);
        return out;
    }

    private Map<String, Object> meta(JsonNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", path(n, "metadata", "name"));
        m.put("namespace", path(n, "metadata", "namespace"));
        m.put("created", path(n, "metadata", "creationTimestamp"));
        return m;
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private String path(JsonNode n, String... p) {
        JsonNode cur = n;
        for (String x : p) cur = cur.path(x);
        return cur.isMissingNode() || cur.isNull() ? "" : cur.asText("");
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String limit(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}

class RealHttpClient {
    private final Environment env;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    RealHttpClient(Environment env, ObjectMapper mapper) {
        this.env = env;
        this.mapper = mapper;
    }

    List<Map<String, Object>> probeTargets(String targets) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String raw : targets.split(",")) {
            String entry = raw.trim();
            if (entry.isBlank()) continue;
            String[] p = entry.split("\\|", -1);
            String name = p.length > 0 && !p[0].isBlank() ? p[0].trim() : "endpoint";
            String url = p.length > 1 ? p[1].trim() : "";
            String authEnv = p.length > 2 ? p[2].trim() : "";
            out.add(probe(name, url, authEnv));
        }
        return out;
    }

    Map<String, Object> probe(String name, String url, String authEnv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("url", redact(url));
        if (url.isBlank()) {
            m.put("state", "NOT_CONFIGURED");
            m.put("message", "Target URL missing.");
            return m;
        }
        long t0 = System.nanoTime();
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().header("Accept", "application/json,text/plain,*/*");
            String auth = authEnv.isBlank() ? "" : env.getProperty(authEnv, "");
            if (!auth.isBlank()) b.header("Authorization", authHeader(auth));
            HttpResponse<String> r = client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            m.put("state", r.statusCode() >= 200 && r.statusCode() < 400 ? "CONNECTED" : "UNAVAILABLE");
            m.put("httpStatus", r.statusCode());
            m.put("latencyMs", Duration.ofNanos(System.nanoTime() - t0).toMillis());
            m.put("contentType", r.headers().firstValue("content-type").orElse(""));
            m.put("summary", summarizeBody(r.body()));
            return m;
        } catch (Exception e) {
            m.put("state", "UNAVAILABLE");
            m.put("latencyMs", Duration.ofNanos(System.nanoTime() - t0).toMillis());
            m.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            return m;
        }
    }

    private String authHeader(String value) {
        String v = value.trim();
        if (v.regionMatches(true, 0, "Bearer ", 0, 7) || v.regionMatches(true, 0, "Basic ", 0, 6)) return v;
        if (v.contains(":")) return "Basic " + Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + v;
    }

    private Object summarizeBody(String body) {
        if (body == null || body.isBlank()) return "";
        String b = body.trim();
        try {
            JsonNode node = mapper.readTree(b);
            if (node.isObject()) {
                Map<String, Object> map = new LinkedHashMap<>();
                node.fieldNames().forEachRemaining(k -> {
                    JsonNode v = node.get(k);
                    if (sensitiveKey(k)) map.put(k, "***");
                    else if (v.isContainerNode()) map.put(k, v.isArray() ? "array[" + v.size() + "]" : "object[" + v.size() + "]");
                    else map.put(k, v.asText());
                });
                return map;
            }
            if (node.isArray()) return "array[" + node.size() + "]";
        } catch (Exception ignored) {}
        return b.length() <= 1000 ? b : b.substring(0, 1000) + "…";
    }

    private boolean sensitiveKey(String key) {
        String k = key == null ? "" : key.toLowerCase();
        return k.contains("token") || k.contains("password") || k.contains("secret") || k.contains("apikey") || k.contains("authorization");
    }

    private String redact(String url) {
        return url.replaceAll("(?i)(token|password|secret|apikey|access_token|client_secret)=([^&]+)", "$1=***");
    }
}

class KubernetesApiClient {
    private final Environment env;
    private final ObjectMapper mapper;
    private final HttpClient client;

    KubernetesApiClient(Environment env, ObjectMapper mapper) {
        this.env = env;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).sslContext(sslContext()).build();
    }

    boolean configured() {
        return env.getProperty("KUBERNETES_SERVICE_HOST") != null && Files.exists(tokenPath());
    }

    JsonNode getJson(String path) throws Exception {
        String host = env.getProperty("KUBERNETES_SERVICE_HOST", "kubernetes.default.svc");
        String port = env.getProperty("KUBERNETES_SERVICE_PORT_HTTPS", env.getProperty("KUBERNETES_SERVICE_PORT", "443"));
        String token = Files.readString(tokenPath()).trim();
        URI uri = URI.create("https://" + host + ":" + port + path);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() < 200 || r.statusCode() >= 300) {
            throw new IllegalStateException("Kubernetes API returned HTTP " + r.statusCode() + " for " + path + ": " + r.body());
        }
        return mapper.readTree(r.body());
    }

    private Path tokenPath() {
        return Path.of(env.getProperty("KUBERNETES_TOKEN_FILE", "/var/run/secrets/kubernetes.io/serviceaccount/token"));
    }

    private SSLContext sslContext() {
        try {
            Path caPath = Path.of(env.getProperty("KUBERNETES_CA_FILE", "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt"));
            if (!Files.exists(caPath)) return SSLContext.getDefault();
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate ca;
            try (InputStream in = Files.newInputStream(caPath)) { ca = cf.generateCertificate(in); }
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            ks.setCertificateEntry("kubernetes-ca", ca);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx;
        } catch (Exception e) {
            try { return SSLContext.getDefault(); } catch (Exception ex) { throw new IllegalStateException(ex); }
        }
    }
}
