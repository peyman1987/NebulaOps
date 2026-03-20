package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.client.DockerSocketClient;
import dev.nebulaops.gateway.client.HttpApiClient;
import dev.nebulaops.gateway.service.DockerRuntimeService;
import dev.nebulaops.gateway.service.KubernetesPlatformService;
import dev.nebulaops.gateway.service.ObservabilityPlatformService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * v23.1 — Operational excellence APIs.
 *
 * These endpoints power the new Runtime Readiness, Docker Storage & Cleanup,
 * Environment Configuration, Dependency Impact and Quality Dashboard views.
 * Every row is derived from live probes, Docker Engine responses, Kubernetes
 * commands, environment variables or generated report files. No frontend mock
 * or seed records are needed.
 */
@RestController
@RequestMapping("/api/platform")
@SuppressWarnings({"unchecked", "rawtypes"})
public class OperationalExcellenceController {

    private final HttpApiClient http;
    private final DockerRuntimeService docker;
    private final DockerSocketClient dockerSocket;
    private final KubernetesPlatformService kubernetes;
    private final ObservabilityPlatformService observability;

    public OperationalExcellenceController(HttpApiClient http,
                                           DockerRuntimeService docker,
                                           DockerSocketClient dockerSocket,
                                           KubernetesPlatformService kubernetes,
                                           ObservabilityPlatformService observability) {
        this.http = http;
        this.docker = docker;
        this.dockerSocket = dockerSocket;
        this.kubernetes = kubernetes;
        this.observability = observability;
    }

    @GetMapping("/readiness")
    public Map<String, Object> readiness() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(dockerReadiness());
        items.add(kubernetesReadiness());
        items.addAll(serviceProbes());
        items.addAll(integrationProbes());
        items.addAll(remoteBundleProbes());
        Map<String, Object> out = base("runtime-readiness");
        out.put("items", items);
        out.put("summary", summary(items));
        out.put("recommendations", recommendations(items));
        return out;
    }

    @GetMapping("/readiness/services")
    public Map<String, Object> readinessServices() {
        List<Map<String, Object>> items = serviceProbes();
        Map<String, Object> out = base("runtime-readiness-services");
        out.put("items", items);
        out.put("summary", summary(items));
        return out;
    }

    @GetMapping("/readiness/integrations")
    public Map<String, Object> readinessIntegrations() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(dockerReadiness());
        items.add(kubernetesReadiness());
        items.addAll(integrationProbes());
        Map<String, Object> out = base("runtime-readiness-integrations");
        out.put("items", items);
        out.put("summary", summary(items));
        return out;
    }

    @GetMapping("/readiness/remotes")
    public Map<String, Object> readinessRemotes() {
        List<Map<String, Object>> items = remoteBundleProbes();
        Map<String, Object> out = base("runtime-readiness-remotes");
        out.put("items", items);
        out.put("summary", summary(items));
        return out;
    }

    @GetMapping("/environment")
    public Map<String, Object> environment() {
        List<Map<String, Object>> items = configurationRows();
        Map<String, Object> out = base("environment-configuration");
        out.put("items", items);
        out.put("summary", summary(items));
        out.put("risks", configurationRisks(items));
        out.put("rules", List.of(
                "Secrets are never returned in clear text.",
                "Missing optional tools disable related actions instead of showing mock data.",
                "Missing required runtime variables are surfaced as NOT_CONFIGURED rows."));
        return out;
    }

    @GetMapping("/environment/variables")
    public Map<String, Object> environmentVariables() {
        List<Map<String, Object>> items = configurationRows();
        Map<String, Object> out = base("environment-variables");
        out.put("items", items);
        out.put("summary", summary(items));
        return out;
    }

    @GetMapping("/environment/feature-flags")
    public Map<String, Object> featureFlags() {
        List<Map<String, Object>> items = System.getenv().entrySet().stream()
                .filter(e -> e.getKey().startsWith("NEBULAOPS_FEATURE_") || e.getKey().startsWith("FEATURE_"))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> configRow(e.getKey(), e.getValue(), false, "feature-flag", false))
                .collect(Collectors.toList());
        Map<String, Object> out = base("feature-flags");
        out.put("items", items);
        out.put("summary", summary(items));
        return out;
    }

    @GetMapping("/environment/risks")
    public Map<String, Object> environmentRisks() {
        List<Map<String, Object>> items = configurationRisks(configurationRows());
        Map<String, Object> out = base("environment-risks");
        out.put("items", items);
        out.put("summary", summary(items));
        return out;
    }

    @GetMapping("/docker/storage")
    public Map<String, Object> dockerStorage() {
        Map<String, Object> status = docker.status();
        Map<String, Object> df = Boolean.TRUE.equals(status.get("ok")) ? dockerSocket.object("/system/df") : Map.of();
        List<Map<String, Object>> items = storageRows(status, df);
        Map<String, Object> out = base("docker-storage");
        out.put("live", Boolean.TRUE.equals(status.get("ok")));
        out.put("toolStatus", status);
        out.put("items", items);
        out.put("systemDf", df);
        out.put("summary", summary(items));
        return out;
    }

    @GetMapping("/docker/storage/prune-preview")
    public Map<String, Object> dockerPrunePreview() {
        Map<String, Object> status = docker.status();
        List<Map<String, Object>> items = new ArrayList<>();
        if (Boolean.TRUE.equals(status.get("ok"))) {
            previewStoppedContainers(items);
            previewDanglingImages(items);
            previewUnusedVolumes(items);
            previewCustomNetworks(items);
        }
        Map<String, Object> out = base("docker-prune-preview");
        out.put("live", Boolean.TRUE.equals(status.get("ok")));
        out.put("toolStatus", status);
        out.put("items", items);
        out.put("dangerZone", "Volume prune can remove real database/cache/broker data. Use only after inspecting the listed volumes.");
        out.put("summary", summary(items));
        return out;
    }

    @GetMapping("/dependency-impact")
    public Map<String, Object> dependencyImpact() {
        List<Map<String, Object>> probes = new ArrayList<>();
        probes.add(dockerReadiness());
        probes.add(kubernetesReadiness());
        probes.addAll(serviceProbes());
        probes.addAll(integrationProbes());
        List<Map<String, Object>> items = dependencyEdges(probes);
        Map<String, Object> out = base("dependency-impact");
        out.put("items", items);
        out.put("nodes", probes);
        out.put("unavailable", probes.stream().filter(p -> !Boolean.TRUE.equals(p.get("live"))).toList());
        out.put("summary", summary(items));
        return out;
    }

    @GetMapping("/dependency-impact/graph")
    public Map<String, Object> dependencyGraph() {
        return dependencyImpact();
    }

    @GetMapping("/dependency-impact/unavailable")
    public Map<String, Object> dependencyUnavailable() {
        List<Map<String, Object>> probes = new ArrayList<>();
        probes.add(dockerReadiness());
        probes.add(kubernetesReadiness());
        probes.addAll(serviceProbes());
        probes.addAll(integrationProbes());
        List<Map<String, Object>> items = probes.stream().filter(p -> !Boolean.TRUE.equals(p.get("live"))).toList();
        Map<String, Object> out = base("dependency-impact-unavailable");
        out.put("items", items);
        out.put("summary", summary(items));
        return out;
    }

    @GetMapping("/quality")
    public Map<String, Object> quality() {
        List<Map<String, Object>> items = qualityReportRows();
        Map<String, Object> out = base("test-quality-dashboard");
        out.put("items", items);
        out.put("summary", summary(items));
        out.put("recommendedCommand", "./scripts/wsl/preflight-v23.1.sh");
        return out;
    }

    @GetMapping("/quality/reports")
    public Map<String, Object> qualityReports() {
        List<Map<String, Object>> items = qualityReportRows();
        Map<String, Object> out = base("quality-reports");
        out.put("items", items);
        out.put("summary", summary(items));
        return out;
    }

    @GetMapping("/quality/preflight")
    public Map<String, Object> qualityPreflight() {
        List<Map<String, Object>> items = List.of(
                fileRow("package-validation", "reports/package-validation.json", true),
                fileRow("frontend-build", "reports/frontend-build.json", true),
                fileRow("backend-build", "reports/backend-build.json", true),
                fileRow("smoke-test", "reports/smoke-test.json", true),
                fileRow("v23.1-preflight-script", "scripts/wsl/preflight-v23.1.sh", true),
                fileRow("remote-verifier", "frontend/tools/verify-remotes.mjs", true)
        );
        Map<String, Object> out = base("quality-preflight");
        out.put("items", items);
        out.put("summary", summary(items));
        return out;
    }

    private Map<String, Object> dockerReadiness() {
        Map<String, Object> status = docker.status();
        boolean live = Boolean.TRUE.equals(status.get("ok"));
        Map<String, Object> row = baseRow("docker", "Docker Engine", "runtime", live);
        row.put("state", status.getOrDefault("state", live ? "READY" : "UNAVAILABLE"));
        row.put("message", status.getOrDefault("message", "Docker Engine status returned by Unix socket."));
        row.put("probe", status);
        return row;
    }

    private Map<String, Object> kubernetesReadiness() {
        Map<String, Object> cluster = kubernetes.cluster();
        boolean live = Boolean.TRUE.equals(cluster.get("live"));
        Map<String, Object> row = baseRow("kubernetes", "Kubernetes Context", "runtime", live);
        row.put("state", live ? "READY" : "KUBERNETES_CONTEXT_UNAVAILABLE");
        row.put("message", live ? "kubectl context is available." : stateFromTool(cluster));
        row.put("probe", cluster);
        return row;
    }

    private List<Map<String, Object>> serviceProbes() {
        List<Map<String, Object>> rows = new ArrayList<>();
        service(rows, "gateway-service", "API Gateway", env("GATEWAY_SERVICE_URL", "http://gateway-service:8080"), "/actuator/health", "backend");
        service(rows, "auth-service", "Identity API", env("AUTH_SERVICE_URL", "http://auth-service:8081"), "/actuator/health", "backend");
        service(rows, "task-service", "Task API", env("TASK_SERVICE_URL", "http://task-service:8082"), "/actuator/health", "backend");
        service(rows, "notification-service", "Notification API", env("NOTIFICATION_SERVICE_URL", "http://notification-service:8083"), "/actuator/health", "backend");
        service(rows, "ai-ops-service", "AI Ops API", env("AI_OPS_SERVICE_URL", "http://ai-ops-service:8090"), "/actuator/health", "backend");
        service(rows, "observability-service", "Observability API", env("OBSERVABILITY_SERVICE_URL", "http://observability-service:8091"), "/actuator/health", "backend");
        service(rows, "release-orchestrator-service", "Release Orchestrator API", env("RELEASE_ORCHESTRATOR_SERVICE_URL", "http://release-orchestrator-service:8098"), "/actuator/health", "backend");
        service(rows, "policy-governance-service", "Policy Governance API", env("POLICY_GOVERNANCE_SERVICE_URL", "http://policy-governance-service:8100"), "/actuator/health", "backend");
        service(rows, "audit-service", "Audit API", env("AUDIT_SERVICE_URL", "http://audit-service:8101"), "/actuator/health", "backend");
        service(rows, "progressive-delivery-service", "Progressive Delivery API", env("PROGRESSIVE_DELIVERY_SERVICE_URL", "http://progressive-delivery-service:8102"), "/actuator/health", "backend");
        return rows;
    }

    private List<Map<String, Object>> integrationProbes() {
        List<Map<String, Object>> rows = new ArrayList<>();
        service(rows, "keycloak", "Keycloak", env("KEYCLOAK_URL", "http://keycloak:8080"), "/realms/" + env("KEYCLOAK_REALM", "nebulaops"), "identity");
        service(rows, "rabbitmq", "RabbitMQ Management", env("RABBITMQ_HTTP_URL", "http://rabbitmq:15672"), "/api/overview", "messaging");
        service(rows, "redis", "Redis Commander", env("REDIS_COMMANDER_URL", "http://redis-commander:8081"), "/", "cache");
        service(rows, "mongodb", "Mongo Express", env("MONGO_EXPRESS_URL", "http://mongo-express:8081"), "/", "database");
        service(rows, "prometheus", "Prometheus", env("PROMETHEUS_URL", "http://prometheus:9090"), "/-/ready", "observability");
        service(rows, "grafana", "Grafana", env("GRAFANA_URL", "http://grafana:3000"), "/api/health", "observability");
        service(rows, "loki", "Loki", env("LOKI_URL", "http://loki:3100"), "/ready", "observability");
        service(rows, "tempo", "Tempo", env("TEMPO_URL", "http://tempo:3200"), "/ready", "observability");
        service(rows, "opa", "OPA", env("OPA_URL", "http://opa:8181"), "/health", "governance");
        service(rows, "gitlab", "GitLab", env("GITLAB_URL", "http://gitlab:80"), "/-/readiness", "devops");
        service(rows, "argocd", "Argo CD", env("ARGOCD_URL", "http://argocd-server.argocd.svc.cluster.local"), "/api/version", "gitops");
        return rows;
    }

    private void service(List<Map<String, Object>> rows, String id, String name, String baseUrl, String path, String category) {
        Map<String, Object> probe = http.get(trimSlash(baseUrl) + path);
        boolean live = Boolean.TRUE.equals(probe.get("live"));
        Map<String, Object> row = baseRow(id, name, category, live);
        row.put("endpoint", baseUrl);
        row.put("path", path);
        row.put("status", probe.getOrDefault("statusCode", 0));
        row.put("durationMs", probe.getOrDefault("durationMs", 0));
        row.put("state", live ? "READY" : "UNREACHABLE");
        row.put("message", live ? "Endpoint responded with HTTP " + probe.get("statusCode") : String.valueOf(probe.getOrDefault("error", "endpoint unreachable")));
        row.put("probe", probe);
        rows.add(row);
    }

    private List<Map<String, Object>> remoteBundleProbes() {
        return remoteDefinitions().stream().map(def -> {
            String id = str(def.get("id"));
            Path path = projectRoot().resolve("frontend/remotes").resolve(id).resolve("dist/browser/remoteEntry.js");
            boolean exists = Files.exists(path);
            Map<String, Object> row = baseRow(id, str(def.get("title")), "micro-frontend", exists);
            row.put("service", def.get("service"));
            row.put("port", def.get("port"));
            row.put("path", path.toString());
            row.put("state", exists ? "BUNDLE_AVAILABLE" : "BUNDLE_NOT_BUILT_OR_NOT_MOUNTED");
            row.put("message", exists ? "Standalone remote bundle is present." : "Run npm run build:remotes or mount the project root into the gateway container to inspect bundle files.");
            return row;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> configurationRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        required(rows, "PUBLIC_ORIGIN", false, "frontend");
        required(rows, "KEYCLOAK_URL", false, "identity");
        required(rows, "KEYCLOAK_REALM", false, "identity");
        required(rows, "KEYCLOAK_ISSUER_URI", false, "identity");
        required(rows, "MONGODB_URI", true, "data");
        required(rows, "REDIS_HOST", false, "cache");
        required(rows, "RABBITMQ_HOST", false, "messaging");
        optional(rows, "PROMETHEUS_URL", false, "observability");
        optional(rows, "LOKI_URL", false, "observability");
        optional(rows, "TEMPO_URL", false, "observability");
        optional(rows, "GRAFANA_URL", false, "observability");
        optional(rows, "OPA_URL", false, "governance");
        optional(rows, "GITLAB_URL", false, "devops");
        optional(rows, "ARGOCD_URL", false, "gitops");
        optional(rows, "DOCKER_HOST", false, "runtime");
        optional(rows, "KUBECONFIG", true, "runtime");
        optional(rows, "NEBULAOPS_HTTP_CONNECT_TIMEOUT_MS", false, "runtime");
        optional(rows, "NEBULAOPS_HTTP_READ_TIMEOUT_MS", false, "runtime");
        optional(rows, "NEBULAOPS_PROJECT_ROOT", false, "quality");
        return rows;
    }

    private void required(List<Map<String, Object>> rows, String key, boolean secret, String category) {
        rows.add(configRow(key, System.getenv(key), true, category, secret));
    }

    private void optional(List<Map<String, Object>> rows, String key, boolean secret, String category) {
        rows.add(configRow(key, System.getenv(key), false, category, secret));
    }

    private Map<String, Object> configRow(String key, String value, boolean required, String category, boolean secret) {
        boolean configured = value != null && !value.isBlank();
        Map<String, Object> row = baseRow(key, key, category, configured || !required);
        row.put("configured", configured);
        row.put("required", required);
        row.put("secret", secret || looksSecret(key));
        row.put("state", configured ? "CONFIGURED" : required ? "NOT_CONFIGURED" : "OPTIONAL_NOT_CONFIGURED");
        row.put("safeValue", configured ? mask(key, value) : "");
        row.put("message", configured ? "Configuration value is present." : required ? "Required configuration missing." : "Optional integration is not configured.");
        return row;
    }

    private List<Map<String, Object>> configurationRisks(List<Map<String, Object>> rows) {
        return rows.stream()
                .filter(r -> Boolean.TRUE.equals(r.get("required")) && !Boolean.TRUE.equals(r.get("configured")))
                .map(r -> {
                    Map<String, Object> risk = baseRow(str(r.get("id")), str(r.get("name")), str(r.get("category")), false);
                    risk.put("severity", "HIGH");
                    risk.put("state", "REQUIRED_CONFIGURATION_MISSING");
                    risk.put("message", "Configure " + r.get("name") + " before running production workflows.");
                    return risk;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> storageRows(Map<String, Object> status, Map<String, Object> df) {
        if (!Boolean.TRUE.equals(status.get("ok"))) {
            Map<String, Object> unavailable = baseRow("docker-storage", "Docker storage", "docker", false);
            unavailable.put("state", status.getOrDefault("state", "DOCKER_UNAVAILABLE"));
            unavailable.put("message", status.getOrDefault("message", "Docker Engine is unavailable."));
            return List.of(unavailable);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        storageCollection(rows, "images", "Images", df.get("Images"), "SharedSize", "Size");
        storageCollection(rows, "containers", "Containers", df.get("Containers"), "SizeRw", "SizeRootFs");
        storageCollection(rows, "volumes", "Volumes", df.get("Volumes"), "Size", "Size");
        storageCollection(rows, "build-cache", "Build Cache", df.get("BuildCache"), "Size", "Size");
        return rows;
    }

    private void storageCollection(List<Map<String, Object>> rows, String id, String name, Object raw, String primarySize, String fallbackSize) {
        if (!(raw instanceof List<?> list)) return;
        long bytes = 0;
        for (Object item : list) if (item instanceof Map<?, ?> m) bytes += asLong(((Map) m).getOrDefault(primarySize, ((Map) m).get(fallbackSize)));
        Map<String, Object> row = baseRow(id, name, "docker-storage", true);
        row.put("count", list.size());
        row.put("bytes", bytes);
        row.put("humanSize", humanBytes(bytes));
        row.put("state", list.isEmpty() ? "EMPTY" : "AVAILABLE");
        row.put("items", list);
        rows.add(row);
    }

    private void previewStoppedContainers(List<Map<String, Object>> items) {
        Object raw = docker.containers().get("items");
        if (raw instanceof List<?> list) for (Object o : list) if (o instanceof Map<?, ?> m) {
            Map map = (Map) m;
            String state = str(map.get("State"));
            if (!"running".equalsIgnoreCase(state)) items.add(pruneRow("container", str(map.get("Id")), firstName(map.get("Names")), state, map));
        }
    }

    private void previewDanglingImages(List<Map<String, Object>> items) {
        Object raw = docker.images().get("items");
        if (raw instanceof List<?> list) for (Object o : list) if (o instanceof Map<?, ?> m) {
            Map map = (Map) m;
            Object repoTags = map.get("RepoTags");
            boolean dangling = repoTags == null || str(repoTags).contains("<none>") || (repoTags instanceof List<?> l && l.isEmpty());
            if (dangling) items.add(pruneRow("image", str(map.get("Id")), str(repoTags), "DANGLING", map));
        }
    }

    private void previewUnusedVolumes(List<Map<String, Object>> items) {
        Object raw = docker.volumes().get("items");
        if (raw instanceof List<?> list) for (Object o : list) if (o instanceof Map<?, ?> m) {
            Map map = (Map) m;
            Map usage = map.get("UsageData") instanceof Map<?, ?> u ? (Map) u : Map.of();
            long refCount = asLong(usage.get("RefCount"));
            if (refCount <= 0) items.add(pruneRow("volume", str(map.get("Name")), str(map.get("Mountpoint")), "UNUSED_OR_DANGLING", map));
        }
    }

    private void previewCustomNetworks(List<Map<String, Object>> items) {
        Object raw = docker.networks().get("items");
        if (raw instanceof List<?> list) for (Object o : list) if (o instanceof Map<?, ?> m) {
            Map map = (Map) m;
            String name = str(map.get("Name"));
            if (!Set.of("bridge", "host", "none").contains(name)) items.add(pruneRow("network", str(map.get("Id")), name, "CUSTOM_NETWORK", map));
        }
    }

    private Map<String, Object> pruneRow(String type, String id, String name, String state, Map raw) {
        Map<String, Object> row = baseRow(id, name == null || name.isBlank() ? id : name, "docker-prune", true);
        row.put("type", type);
        row.put("state", state);
        row.put("message", "Candidate identified from live Docker Engine data. Review before executing prune actions.");
        row.put("raw", raw);
        return row;
    }

    private List<Map<String, Object>> dependencyEdges(List<Map<String, Object>> probes) {
        Map<String, Map<String, Object>> byId = probes.stream().collect(Collectors.toMap(p -> str(p.get("id")), p -> p, (a, b) -> a, LinkedHashMap::new));
        List<String[]> edges = List.of(
                new String[]{"platform-catalog", "gateway-service"},
                new String[]{"runtime-readiness", "gateway-service"},
                new String[]{"environment-configuration", "gateway-service"},
                new String[]{"docker-storage-cleanup", "docker"},
                new String[]{"dependency-impact", "gateway-service"},
                new String[]{"test-quality-dashboard", "gateway-service"},
                new String[]{"incident-command-center", "observability-service"},
                new String[]{"incident-command-center", "task-service"},
                new String[]{"incident-command-center", "notification-service"},
                new String[]{"incident-command-center", "audit-service"},
                new String[]{"incident-command-center", "kubernetes"},
                new String[]{"release-center", "release-orchestrator-service"},
                new String[]{"progressive-delivery", "progressive-delivery-service"},
                new String[]{"policy-center", "policy-governance-service"},
                new String[]{"observability", "prometheus"},
                new String[]{"observability", "loki"},
                new String[]{"observability", "tempo"},
                new String[]{"identity-admin", "keycloak"},
                new String[]{"task-management", "rabbitmq"},
                new String[]{"task-management", "mongodb"}
        );
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String[] edge : edges) {
            Map<String, Object> target = byId.get(edge[1]);
            boolean live = target == null || Boolean.TRUE.equals(target.get("live"));
            Map<String, Object> row = baseRow(edge[0] + "->" + edge[1], edge[0] + " → " + edge[1], "dependency", live);
            row.put("source", edge[0]);
            row.put("target", edge[1]);
            row.put("targetState", target == null ? "NOT_PROBED_IN_THIS_VIEW" : target.get("state"));
            row.put("impact", live ? "Dependency currently available or not probed." : "Dependent module may be degraded until this dependency recovers.");
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> qualityReportRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(fileRow("frontend-dist", "frontend/dist/nebulaops/browser/index.html", true));
        rows.add(fileRow("remote-verification-tool", "frontend/tools/verify-remotes.mjs", true));
        rows.add(fileRow("package-validator", "scripts/validate-package.py", true));
        rows.add(fileRow("yaml-validator", "scripts/validate-yaml.py", true));
        rows.add(fileRow("preflight-v23.1", "scripts/wsl/preflight-v23.1.sh", true));
        rows.add(fileRow("presentation-live-data-audit", "V23.1_PRESENTATION_LIVE_DATA_AUDIT_REPORT.md", true));
        rows.add(fileRow("frontend-build-report", "reports/frontend-build.json", false));
        rows.add(fileRow("backend-build-report", "reports/backend-build.json", false));
        rows.add(fileRow("smoke-test-report", "reports/smoke-test.json", false));
        rows.add(fileRow("package-validation-report", "reports/package-validation.json", false));
        return rows;
    }

    private Map<String, Object> fileRow(String id, String relativePath, boolean required) {
        Path path = projectRoot().resolve(relativePath).normalize();
        boolean exists = Files.exists(path);
        Map<String, Object> row = baseRow(id, id, "quality", exists || !required);
        row.put("path", path.toString());
        row.put("required", required);
        row.put("state", exists ? "FOUND" : required ? "MISSING_REQUIRED_FILE" : "REPORT_NOT_GENERATED");
        row.put("message", exists ? "File exists on the runtime filesystem." : required ? "Required validation input is missing." : "Report not generated yet. Run the preflight/smoke command to produce it.");
        try {
            if (exists) row.put("sizeBytes", Files.size(path));
            if (exists) row.put("modifiedAt", Files.getLastModifiedTime(path).toString());
        } catch (Exception e) {
            row.put("fileError", e.getMessage());
        }
        return row;
    }

    private Map<String, Object> base(String source) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", source);
        out.put("generatedAt", Instant.now().toString());
        out.put("realDataOnly", true);
        return out;
    }

    private Map<String, Object> baseRow(String id, String name, String category, boolean live) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("category", category);
        row.put("live", live);
        row.put("checkedAt", Instant.now().toString());
        return row;
    }

    private Map<String, Object> summary(List<Map<String, Object>> items) {
        long live = items.stream().filter(i -> Boolean.TRUE.equals(i.get("live"))).count();
        long degraded = items.size() - live;
        return Map.of("total", items.size(), "live", live, "degraded", degraded, "status", degraded == 0 ? "READY" : "DEGRADED");
    }

    private List<Map<String, Object>> recommendations(List<Map<String, Object>> items) {
        return items.stream().filter(i -> !Boolean.TRUE.equals(i.get("live"))).map(i -> {
            Map<String, Object> row = baseRow(str(i.get("id")) + "-recommendation", "Fix " + i.get("name"), "recommendation", false);
            row.put("state", i.getOrDefault("state", "DEGRADED"));
            row.put("message", i.getOrDefault("message", "Open the linked runtime module and inspect the failing endpoint."));
            return row;
        }).toList();
    }

    private List<Map<String, Object>> remoteDefinitions() {
        return List.of(
                remote("platform-catalog", "Platform Catalog & Service Registry", "mfe-platform-catalog", 4220),
                remote("incident-command-center", "Incident Command Center", "mfe-incident-command-center", 4227),
                remote("runtime-readiness", "Runtime Readiness Dashboard", "mfe-runtime-readiness", 4228),
                remote("docker-storage-cleanup", "Docker Storage & Cleanup Center", "mfe-docker-storage-cleanup", 4229),
                remote("environment-configuration", "Environment & Configuration Center", "mfe-environment-configuration", 4230),
                remote("dependency-impact", "Dependency & Impact Graph", "mfe-dependency-impact", 4231),
                remote("test-quality-dashboard", "Test & Quality Dashboard", "mfe-test-quality-dashboard", 4232),
                remote("docker-desktop", "Docker Desktop", "mfe-docker-desktop", 4211),
                remote("openlens-kubernetes", "OpenLens Kubernetes", "mfe-openlens-kubernetes", 4212),
                remote("task-management", "Task Management", "mfe-task-management", 4213),
                remote("observability", "Observability & Audit Center", "mfe-observability", 4214),
                remote("release-center", "Release Center", "mfe-release-center", 4223),
                remote("policy-center", "Policy, Approval & Governance Center", "mfe-policy-center", 4224),
                remote("progressive-delivery", "Progressive Delivery Center", "mfe-progressive-delivery", 4226),
                remote("identity-admin", "Identity Admin", "mfe-identity-admin", 4225)
        );
    }

    private Map<String, Object> remote(String id, String title, String service, int port) {
        return Map.of("id", id, "title", title, "service", service, "port", port);
    }

    private String stateFromTool(Map<String, Object> raw) {
        Object status = raw.get("toolStatus");
        if (status instanceof Map<?, ?> m) {
            Object err = ((Map) m).get("stderr");
            if (err != null && !String.valueOf(err).isBlank()) return String.valueOf(err);
            Object code = ((Map) m).get("exitCode");
            if (code != null) return "Tool exit code " + code;
        }
        return "Runtime tool is unavailable or returned no live data.";
    }

    private Path projectRoot() {
        return Path.of(env("NEBULAOPS_PROJECT_ROOT", "."));
    }

    private String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String trimSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean looksSecret(String key) {
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return k.contains("password") || k.contains("secret") || k.contains("token") || k.contains("uri") || k.contains("key");
    }

    private String mask(String key, String value) {
        if (value == null) return "";
        if (looksSecret(key)) {
            if (value.length() <= 8) return "********";
            return value.substring(0, Math.min(4, value.length())) + "..." + value.substring(Math.max(0, value.length() - 4));
        }
        return value;
    }

    private long asLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(str(value)); } catch (Exception ignored) { return 0L; }
    }

    private String humanBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int i = -1;
        do { value /= 1024.0; i++; } while (value >= 1024 && i < units.length - 1);
        return String.format(Locale.ROOT, "%.2f %s", value, units[i]);
    }

    private String firstName(Object names) {
        if (names instanceof List<?> list && !list.isEmpty()) return str(list.get(0)).replaceFirst("^/+", "");
        return str(names);
    }
}
