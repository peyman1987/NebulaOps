package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.service.DockerRuntimeService;
import dev.nebulaops.gateway.service.KubernetesPlatformService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * v23.2 — Platform Catalog & Service Registry.
 *
 * This controller centralizes MFE/MBE metadata and enriches it with live runtime probes.
 * It never returns frontend-owned mock rows: if a dependency, Docker socket, Kubernetes
 * context or service endpoint is unavailable, the returned component is marked with an
 * explicit runtime state and the failing probe details.
 */
@RestController
@RequestMapping("/api/platform/catalog")
@SuppressWarnings({"unchecked", "rawtypes"})
public class PlatformCatalogController {

    private final RestTemplate rest;
    private final DockerRuntimeService docker;
    private final KubernetesPlatformService kubernetes;

    @Value("${proxy.auth}") private String authUrl;
    @Value("${proxy.task}") private String taskUrl;
    @Value("${proxy.notification}") private String notificationUrl;
    @Value("${proxy.file}") private String fileUrl;
    @Value("${proxy.ai-ops}") private String aiOpsUrl;
    @Value("${proxy.devsecops}") private String devsecopsUrl;
    @Value("${proxy.pipeline}") private String pipelineUrl;
    @Value("${proxy.observability}") private String observabilityUrl;
    @Value("${proxy.gitops}") private String gitopsUrl;
    @Value("${proxy.environment}") private String environmentUrl;
    @Value("${proxy.terraform}") private String terraformUrl;
    @Value("${proxy.cost}") private String costUrl;
    @Value("${proxy.release}") private String releaseUrl;
    @Value("${proxy.policy}") private String policyUrl;
    @Value("${proxy.progressive-delivery}") private String progressiveDeliveryUrl;
    @Value("${proxy.audit}") private String auditUrl;

    public PlatformCatalogController(RestTemplate rest,
                                     DockerRuntimeService docker,
                                     KubernetesPlatformService kubernetes) {
        this.rest = rest;
        this.docker = docker;
        this.kubernetes = kubernetes;
    }

    @GetMapping({"", "/"})
    public Map<String, Object> catalog() {
        List<Map<String, Object>> items = loadComponents();
        Map<String, Object> out = base("platform-catalog");
        out.put("items", items);
        out.put("dependencies", dependencyEdges(items));
        out.put("openapi", openApiLinks(items));
        out.put("summary", summary(items));
        out.put("runtime", runtimeStatus());
        return out;
    }

    @GetMapping("/components")
    public Map<String, Object> components() {
        List<Map<String, Object>> items = loadComponents();
        Map<String, Object> out = base("platform-catalog-components");
        out.put("items", items);
        out.put("summary", summary(items));
        return out;
    }

    @GetMapping("/remotes")
    public Map<String, Object> remotes() {
        List<Map<String, Object>> items = filter(loadComponents(), "MICRO_FRONTEND");
        Map<String, Object> out = base("platform-catalog-remotes");
        out.put("items", items);
        out.put("summary", summary(items));
        return out;
    }

    @GetMapping("/backends")
    public Map<String, Object> backends() {
        List<Map<String, Object>> items = filter(loadComponents(), "BACKEND_SERVICE");
        Map<String, Object> out = base("platform-catalog-backends");
        out.put("items", items);
        out.put("summary", summary(items));
        return out;
    }

    @GetMapping("/dependencies")
    public Map<String, Object> dependencies() {
        List<Map<String, Object>> items = loadComponents();
        Map<String, Object> out = base("platform-catalog-dependencies");
        out.put("items", dependencyEdges(items));
        return out;
    }

    @GetMapping("/openapi")
    public Map<String, Object> openapi() {
        List<Map<String, Object>> items = loadComponents();
        Map<String, Object> out = base("platform-catalog-openapi");
        out.put("items", openApiLinks(items));
        return out;
    }

    @GetMapping("/runtime")
    public Map<String, Object> runtime() {
        Map<String, Object> out = base("platform-catalog-runtime");
        out.putAll(runtimeStatus());
        return out;
    }

    private List<Map<String, Object>> loadComponents() {
        Map<String, Object> dockerStatus = safeDockerStatus();
        List<Map<String, Object>> containers = dockerContainers(dockerStatus);
        List<Map<String, Object>> workloads = kubernetesDeployments();
        List<Map<String, Object>> registry = registry();
        return registry.parallelStream()
                .map(component -> enrich(component, dockerStatus, containers, workloads))
                .toList();
    }

    private List<Map<String, Object>> registry() {
        String frontendUrl = env("FRONTEND_INTERNAL_URL", "http://frontend:80");
        List<Map<String, Object>> items = new ArrayList<>();

        remote(items, frontendUrl, "platform-catalog", "Platform Catalog & Service Registry", 4220, "Platform", "platform-engineering",
                List.of("/api/platform/catalog", "/api/platform/catalog/components", "/api/platform/catalog/dependencies", "/api/platform/catalog/openapi"),
                List.of("gateway-service", "Docker", "Kubernetes", "OpenAPI", "Observability"));
        remote(items, frontendUrl, "docker-desktop", "Docker Desktop", 4211, "Runtime", "platform-runtime",
                List.of("/api/runtime/docker/status", "/api/runtime/docker/containers", "/api/runtime/docker/images", "/api/runtime/docker/volumes", "/api/runtime/docker/networks"),
                List.of("gateway-service", "Docker"));
        remote(items, frontendUrl, "openlens-kubernetes", "OpenLens Kubernetes", 4212, "Runtime", "platform-runtime",
                List.of("/api/kubernetes/kubeconfigs", "/api/kubernetes/cluster", "/api/kubernetes/resources", "/api/kubernetes/apply", "/api/kubernetes/delete", "/api/kubernetes/helm/releases"),
                List.of("gateway-service", "MongoDB", "Kubernetes", "kubectl", "Helm"));
        remote(items, frontendUrl, "task-management", "Task Management", 4213, "Delivery", "delivery-platform",
                List.of("/api/tasks", "/api/audit/events?type=TASK", "/api/observability/events/tasks"),
                List.of("task-service", "notification-service", "audit-service", "RabbitMQ", "MongoDB"));
        remote(items, frontendUrl, "observability", "Observability & Audit Center", 4214, "SRE", "sre-platform",
                List.of("/api/observability/incidents/timeline", "/api/observability/services", "/api/observability/metrics/prometheus", "/api/observability/logs/loki", "/api/observability/traces/tempo"),
                List.of("observability-service", "audit-service", "Prometheus", "Loki", "Tempo", "RabbitMQ"));
        remote(items, frontendUrl, "incident-command-center", "Incident Command Center", 4227, "SRE", "sre-platform",
                List.of("/api/incidents/command-center", "/api/incidents/command-center/incidents", "/api/incidents/command-center/timeline", "/api/incidents/command-center/export"),
                List.of("observability-service", "ai-ops-service", "notification-service", "audit-service", "task-service", "release-orchestrator-service", "Kubernetes", "Loki", "Prometheus", "Tempo"));
        remote(items, frontendUrl, "runtime-readiness", "Runtime Readiness Dashboard", 4228, "Platform", "platform-engineering",
                List.of("/api/platform/readiness", "/api/platform/readiness/services", "/api/platform/readiness/integrations", "/api/platform/readiness/remotes"),
                List.of("gateway-service", "Docker", "Kubernetes", "Keycloak", "RabbitMQ", "MongoDB", "Redis", "Prometheus", "Loki", "Tempo"));
        remote(items, frontendUrl, "docker-storage-cleanup", "Docker Storage & Cleanup Center", 4229, "Runtime", "platform-runtime",
                List.of("/api/platform/docker/storage", "/api/platform/docker/storage/prune-preview", "/api/runtime/docker/system/df", "/api/runtime/docker/system/prune"),
                List.of("gateway-service", "Docker"));
        remote(items, frontendUrl, "environment-configuration", "Environment & Configuration Center", 4230, "Platform", "platform-engineering",
                List.of("/api/platform/environment", "/api/platform/environment/variables", "/api/platform/environment/feature-flags", "/api/platform/environment/risks"),
                List.of("gateway-service", "Environment variables", "Feature flags", "Runtime config"));
        remote(items, frontendUrl, "dependency-impact", "Dependency & Impact Graph", 4231, "Platform", "platform-engineering",
                List.of("/api/platform/dependency-impact", "/api/platform/dependency-impact/graph", "/api/platform/dependency-impact/unavailable"),
                List.of("gateway-service", "Platform Catalog", "Runtime Readiness"));
        remote(items, frontendUrl, "test-quality-dashboard", "Test & Quality Dashboard", 4232, "Quality", "platform-engineering",
                List.of("/api/platform/quality", "/api/platform/quality/reports", "/api/platform/quality/preflight"),
                List.of("gateway-service", "Preflight scripts", "Build reports", "Validation reports"));
        remote(items, frontendUrl, "cicd-gitops", "CI/CD + GitOps", 4215, "DevOps", "devops-platform",
                List.of("/api/pipeline/runs", "/api/runtime/helm/releases"),
                List.of("pipeline-engine-service", "gitops-control-service", "GitLab", "Argo CD"));
        remote(items, frontendUrl, "terraform-studio", "Terraform Studio", 4216, "IaC", "infra-platform",
                List.of("/api/platform/terraform/modules", "/api/platform/terraform/plan", "/api/platform/terraform/graph"),
                List.of("terraform-studio-service"));
        remote(items, frontendUrl, "devsecops", "DevSecOps", 4217, "Security", "security-platform",
                List.of("/api/platform/devsecops", "/api/platform/devsecops/secrets", "/api/registry/images"),
                List.of("devsecops-service", "policy-governance-service", "OPA"));
        remote(items, frontendUrl, "ai-ops", "AI Ops", 4218, "AI", "sre-platform",
                List.of("/api/ai-ops/incidents", "/api/kubernetes/logs", "/api/events"),
                List.of("ai-ops-service", "observability-service", "ai-engine"));
        remote(items, frontendUrl, "finops-cost", "FinOps Cost", 4219, "FinOps", "finops-platform",
                List.of("/api/cost/summary", "/api/cost/services", "/api/cost/forecast", "/api/cost/anomalies"),
                List.of("cost-analytics-service", "Kubernetes", "Prometheus"));
        remote(items, frontendUrl, "infra-hub", "INFRA Hub", 4221, "Runtime", "platform-runtime",
                List.of("/api/platform/infra/services", "/api/platform/infra/ports", "/api/platform/infra/reverse-proxy"),
                List.of("gateway-service", "Docker", "Keycloak", "RabbitMQ", "Redis", "MongoDB", "Prometheus", "Grafana", "OPA", "GitLab", "Argo CD"));
        remote(items, frontendUrl, "release-center", "Release Center", 4222, "Release", "release-platform",
                List.of("/api/releases", "/api/governance/decisions", "/api/governance/approvals"),
                List.of("release-orchestrator-service", "policy-governance-service", "progressive-delivery-service", "GitLab", "Argo CD"));
        remote(items, frontendUrl, "progressive-delivery", "Progressive Delivery Center", 4223, "Release", "release-platform",
                List.of("/api/progressive-delivery/rollouts", "/api/progressive-delivery/applications", "/api/progressive-delivery/analysis-runs"),
                List.of("progressive-delivery-service", "Kubernetes", "Argo Rollouts", "Argo CD"));
        remote(items, frontendUrl, "policy-center", "Policy, Approval & Governance Center", 4224, "Governance", "governance-platform",
                List.of("/api/governance", "/api/governance/policies", "/api/governance/decisions", "/api/governance/approvals"),
                List.of("policy-governance-service", "OPA", "release-orchestrator-service", "devsecops-service", "audit-service"));
        remote(items, frontendUrl, "notification-center", "Notification Center", 4225, "Notifications", "platform-communications",
                List.of("/api/notifications/live", "/api/notifications/preferences", "/api/events"),
                List.of("notification-service", "RabbitMQ", "MongoDB", "audit-service"));
        remote(items, frontendUrl, "identity-admin", "Identity Admin", 4226, "Identity", "identity-platform",
                List.of("/api/identity/realms/nebulaops/status", "/api/identity/realms/nebulaops/users", "/api/identity/realms/nebulaops/groups", "/api/identity/realms/nebulaops/roles"),
                List.of("auth-service", "Keycloak", "Redis"));

        backend(items, "gateway-service", "Gateway / BFF", gatewayUrl(), 8080, "Runtime", "platform-engineering",
                List.of("/api/platform/catalog", "/api/incidents/command-center", "/api/platform/infra/services", "/api/runtime/docker/status", "/api/kubernetes/cluster"),
                List.of("auth-service", "Docker", "Kubernetes", "Keycloak", "observability-service", "ai-ops-service"));
        backend(items, "auth-service", "Auth Service", authUrl, 8081, "Identity", "identity-platform",
                List.of("/api/identity/realms/{realm}/status", "/api/identity/realms/{realm}/users", "/api/identity/realms/{realm}/groups"),
                List.of("Keycloak", "Redis", "MongoDB", "audit-service"));
        backend(items, "task-service", "Task Service", taskUrl, 8082, "Delivery", "delivery-platform",
                List.of("/api/tasks", "/api/tasks/{id}/move", "/api/tasks/{id}/status/{status}"),
                List.of("MongoDB", "RabbitMQ", "audit-service", "notification-service"));
        backend(items, "notification-service", "Notification Service", notificationUrl, 8083, "Notifications", "platform-communications",
                List.of("/api/notifications/live", "/api/notifications/preferences"),
                List.of("MongoDB", "RabbitMQ", "Keycloak"));
        backend(items, "file-service", "File Service", fileUrl, 8084, "Platform", "platform-engineering",
                List.of("/api/files", "/api/files/{id}"), List.of("MongoDB"));
        backend(items, "ai-ops-service", "AI Ops Service", aiOpsUrl, 8085, "AI", "sre-platform",
                List.of("/api/ai-ops/analyze", "/api/ai-ops/incidents"),
                List.of("ai-engine", "observability-service", "Loki", "Prometheus"));
        backend(items, "devsecops-service", "DevSecOps Service", devsecopsUrl, 8086, "Security", "security-platform",
                List.of("/api/devsecops/scans", "/api/secrets/scan"),
                List.of("Trivy", "OPA", "policy-governance-service"));
        backend(items, "pipeline-engine-service", "Pipeline Engine Service", pipelineUrl, 8087, "DevOps", "devops-platform",
                List.of("/api/pipeline/runs", "/api/pipeline/runs/{id}"),
                List.of("GitLab", "RabbitMQ", "audit-service"));
        backend(items, "observability-service", "Observability Service", observabilityUrl, 8092, "SRE", "sre-platform",
                List.of("/api/observability/services", "/api/observability/incidents/timeline", "/api/observability/metrics/prometheus"),
                List.of("Prometheus", "Loki", "Tempo", "RabbitMQ", "audit-service"));
        backend(items, "gitops-control-service", "GitOps Control Service", gitopsUrl, 8093, "DevOps", "devops-platform",
                List.of("/api/gitops/applications", "/api/gitops/sync"),
                List.of("Argo CD", "Kubernetes"));
        backend(items, "environment-manager-service", "Environment Manager Service", environmentUrl, 8094, "Runtime", "platform-runtime",
                List.of("/api/environments", "/api/environments/{id}"),
                List.of("Kubernetes", "Terraform"));
        backend(items, "terraform-studio-service", "Terraform Studio Service", terraformUrl, 8096, "IaC", "infra-platform",
                List.of("/api/terraform/modules", "/api/terraform/plan", "/api/terraform/graph"),
                List.of("Terraform CLI", "GitLab"));
        backend(items, "cost-analytics-service", "Cost Analytics Service", costUrl, 8097, "FinOps", "finops-platform",
                List.of("/api/cost/summary", "/api/cost/services", "/api/cost/forecast"),
                List.of("Prometheus", "Kubernetes"));
        backend(items, "release-orchestrator-service", "Release Orchestrator Service", releaseUrl, 8098, "Release", "release-platform",
                List.of("/api/releases", "/api/releases/{id}/promote", "/api/releases/{id}/rollback"),
                List.of("pipeline-engine-service", "policy-governance-service", "progressive-delivery-service", "audit-service"));
        backend(items, "policy-governance-service", "Policy Governance Service", policyUrl, 8100, "Governance", "governance-platform",
                List.of("/api/governance/policies", "/api/governance/decisions", "/api/governance/approvals"),
                List.of("OPA", "release-orchestrator-service", "devsecops-service", "audit-service"));
        backend(items, "audit-service", "Audit Service", auditUrl, 8101, "Observability", "sre-platform",
                List.of("/api/audit/events", "/api/events"),
                List.of("MongoDB", "RabbitMQ"));
        backend(items, "progressive-delivery-service", "Progressive Delivery Service", progressiveDeliveryUrl, 8102, "Release", "release-platform",
                List.of("/api/progressive-delivery/rollouts", "/api/progressive-delivery/applications", "/api/progressive-delivery/analysis-runs"),
                List.of("Argo Rollouts", "Argo CD", "Kubernetes"));

        infrastructure(items, "keycloak", "Keycloak", env("KEYCLOAK_URL", "http://keycloak:8080"), 8180, "Identity", "/realms/nebulaops", List.of("auth-service", "frontend"));
        infrastructure(items, "rabbitmq", "RabbitMQ", env("RABBITMQ_HTTP_URL", "http://rabbitmq:15672"), 15672, "Data", "/api/overview", List.of("task-service", "notification-service", "audit-service"));
        infrastructure(items, "redis", "Redis", env("REDIS_COMMANDER_URL", "http://redis-commander:8081"), 6379, "Data", "/", List.of("auth-service", "identity-admin"));
        infrastructure(items, "mongodb", "MongoDB", env("MONGO_EXPRESS_URL", "http://mongo-express:8081"), 27017, "Data", "/", List.of("auth-service", "task-service", "notification-service", "audit-service"));
        infrastructure(items, "prometheus", "Prometheus", env("PROMETHEUS_URL", "http://prometheus:9090"), 9090, "Observability", "/-/ready", List.of("observability-service", "finops-cost"));
        infrastructure(items, "grafana", "Grafana", env("GRAFANA_URL", "http://grafana:3000"), 3000, "Observability", "/api/health", List.of("observability", "Prometheus", "Tempo", "Loki"));
        infrastructure(items, "loki", "Loki", env("LOKI_URL", "http://loki:3100"), 3100, "Observability", "/ready", List.of("observability-service"));
        infrastructure(items, "tempo", "Tempo", env("TEMPO_URL", "http://tempo:3200"), 3200, "Observability", "/ready", List.of("observability-service"));
        infrastructure(items, "opa", "OPA", env("OPA_URL", "http://opa:8181"), 8181, "Governance", "/health", List.of("policy-governance-service"));
        infrastructure(items, "gitlab", "GitLab", env("GITLAB_URL", "http://gitlab:80"), 8929, "DevOps", "/-/readiness", List.of("pipeline-engine-service", "release-orchestrator-service"));
        infrastructure(items, "argocd", "Argo CD", env("ARGOCD_URL", "http://argocd-server.argocd.svc.cluster.local"), 8080, "DevOps", "/api/version", List.of("gitops-control-service", "progressive-delivery-service"));
        return items;
    }

    private void remote(List<Map<String, Object>> items, String frontendUrl, String id, String title, int port, String group,
                        String owner, List<String> endpoints, List<String> dependencies) {
        Map<String, Object> row = component("MICRO_FRONTEND", id, title, group, owner, frontendUrl, port,
                "/remotes/" + id + "/remoteEntry.js", "/remotes/" + id + "/", "mfe-" + id,
                "mfe-" + id, null, endpoints, dependencies);
        row.put("tag", "nebulaops-mfe-" + id);
        items.add(row);
    }

    private void backend(List<Map<String, Object>> items, String id, String title, String baseUrl, int port, String group,
                         String owner, List<String> endpoints, List<String> dependencies) {
        items.add(component("BACKEND_SERVICE", id, title, group, owner, baseUrl, port,
                "/actuator/health", null, id, id, baseUrl.replaceAll("/+$", "") + "/v3/api-docs", endpoints, dependencies));
    }

    private void infrastructure(List<Map<String, Object>> items, String id, String title, String baseUrl, int port, String group,
                                String healthPath, List<String> dependencies) {
        items.add(component("INFRASTRUCTURE", id, title, group, "platform-runtime", baseUrl, port,
                healthPath, null, id, id, null, List.of(healthPath), dependencies));
    }

    private Map<String, Object> component(String type, String id, String title, String group, String owner,
                                          String baseUrl, int port, String healthPath, String route,
                                          String dockerService, String kubernetesWorkload, String openApi,
                                          List<String> endpoints, List<String> dependencies) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", title);
        row.put("componentType", type);
        row.put("serviceGroup", group);
        row.put("owner", owner);
        row.put("port", port);
        row.put("baseUrl", baseUrl);
        row.put("route", route);
        row.put("healthEndpoint", healthPath);
        row.put("mainEndpoints", endpoints);
        row.put("dependencies", dependencies);
        row.put("dockerService", dockerService);
        row.put("kubernetesWorkload", kubernetesWorkload);
        row.put("openApi", openApi);
        row.put("documentation", "/docs/" + id + ".md");
        row.put("realDataOnly", true);
        return row;
    }

    private Map<String, Object> enrich(Map<String, Object> component,
                                       Map<String, Object> dockerStatus,
                                       List<Map<String, Object>> containers,
                                       List<Map<String, Object>> workloads) {
        Map<String, Object> row = new LinkedHashMap<>(component);
        Map<String, Object> probe = probe(component);
        Map<String, Object> dockerRuntime = dockerRuntime(row, dockerStatus, containers);
        Map<String, Object> kubernetesRuntime = kubernetesRuntime(row, workloads);
        row.put("live", probe.get("live"));
        row.put("reachable", probe.get("reachable"));
        row.put("status", probe.get("status"));
        row.put("state", probe.get("state"));
        row.put("checkedAt", probe.get("checkedAt"));
        row.put("lastProbe", probe);
        row.put("dockerRuntime", dockerRuntime);
        row.put("kubernetesRuntime", kubernetesRuntime);
        row.put("links", links(row));
        return row;
    }

    private Map<String, Object> probe(Map<String, Object> component) {
        String baseUrl = str(component.get("baseUrl"));
        String path = str(component.get("healthEndpoint"));
        String url = joinUrl(baseUrl, path);
        long started = System.nanoTime();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("url", url);
        out.put("checkedAt", Instant.now().toString());
        out.put("realDataOnly", true);
        try {
            ResponseEntity<String> response = rest.getForEntity(url, String.class);
            int code = response.getStatusCode().value();
            out.put("status", code);
            out.put("reachable", code < 500);
            out.put("live", code >= 200 && code < 400);
            out.put("state", code >= 200 && code < 400 ? "UP" : "HTTP_" + code);
            out.put("body", trim(response.getBody()));
        } catch (Exception e) {
            out.put("status", 0);
            out.put("reachable", false);
            out.put("live", false);
            out.put("state", "UNREACHABLE");
            out.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        out.put("durationMs", (System.nanoTime() - started) / 1_000_000);
        return out;
    }

    private Map<String, Object> dockerRuntime(Map<String, Object> row,
                                              Map<String, Object> dockerStatus,
                                              List<Map<String, Object>> containers) {
        String service = str(row.get("dockerService")).toLowerCase(Locale.ROOT);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dockerSocket", dockerStatus);
        out.put("service", service);
        out.put("live", false);
        if (!Boolean.TRUE.equals(dockerStatus.get("ok"))) {
            out.put("state", dockerStatus.getOrDefault("state", "DOCKER_SOCKET_UNAVAILABLE"));
            return out;
        }
        for (Map<String, Object> container : containers) {
            String name = firstContainerName(container).toLowerCase(Locale.ROOT);
            if (name.contains(service) || service.contains(name)) {
                out.put("live", true);
                out.put("state", container.getOrDefault("State", container.getOrDefault("Status", "RUNNING")));
                out.put("name", firstContainerName(container));
                out.put("image", container.get("Image"));
                out.put("ports", container.get("Ports"));
                out.put("id", container.get("Id"));
                return out;
            }
        }
        out.put("state", "DOCKER_CONTAINER_NOT_FOUND");
        return out;
    }

    private Map<String, Object> kubernetesRuntime(Map<String, Object> row, List<Map<String, Object>> workloads) {
        String workload = str(row.get("kubernetesWorkload")).toLowerCase(Locale.ROOT);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("workload", workload);
        out.put("live", false);
        for (Map<String, Object> item : workloads) {
            Map meta = asMap(item.get("metadata"));
            String name = str(meta.get("name")).toLowerCase(Locale.ROOT);
            if (name.equals(workload) || name.contains(workload) || workload.contains(name)) {
                Map status = asMap(item.get("status"));
                out.put("live", true);
                out.put("state", status.getOrDefault("readyReplicas", 0) + "/" + status.getOrDefault("replicas", 0));
                out.put("namespace", meta.get("namespace"));
                out.put("name", meta.get("name"));
                out.put("rawStatus", status);
                return out;
            }
        }
        out.put("state", workloads.isEmpty() ? "KUBERNETES_CONTEXT_UNAVAILABLE_OR_NO_DEPLOYMENTS" : "KUBERNETES_WORKLOAD_NOT_FOUND");
        return out;
    }

    private Map<String, Object> links(Map<String, Object> row) {
        String id = str(row.get("id"));
        String route = str(row.get("route"));
        String openApi = str(row.get("openApi"));
        Map<String, Object> links = new LinkedHashMap<>();
        if (!route.isBlank()) links.put("ui", route);
        if (!openApi.isBlank()) links.put("openapi", openApi);
        links.put("docs", row.get("documentation"));
        links.put("logs", "/api/observability/logs/loki?query={service=\"" + id + "\"}");
        links.put("metrics", "/api/observability/metrics/prometheus?query=up{job=\"" + id + "\"}");
        links.put("traces", "/api/observability/traces/tempo?service=" + id);
        links.put("release", "/api/releases?component=" + id);
        links.put("policy", "/api/governance/decisions?component=" + id);
        return links;
    }

    private Map<String, Object> runtimeStatus() {
        Map<String, Object> dockerStatus = safeDockerStatus();
        Map<String, Object> k8sDeployments = Map.of("live", false, "state", "NOT_CHECKED");
        try {
            k8sDeployments = kubernetes.resource("deployments", "all");
        } catch (Exception e) {
            k8sDeployments = Map.of("live", false, "state", "KUBERNETES_CONTEXT_UNAVAILABLE", "error", e.getMessage());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("docker", dockerStatus);
        out.put("kubernetesDeployments", k8sDeployments);
        return out;
    }

    private List<Map<String, Object>> dockerContainers(Map<String, Object> dockerStatus) {
        if (!Boolean.TRUE.equals(dockerStatus.get("ok"))) return List.of();
        Map<String, Object> response = docker.containers();
        return mapList(response.get("items"));
    }

    private Map<String, Object> safeDockerStatus() {
        try {
            return docker.status();
        } catch (Exception e) {
            return Map.of("ok", false, "state", "DOCKER_SOCKET_UNAVAILABLE", "error", e.getMessage(), "realDataOnly", true);
        }
    }

    private List<Map<String, Object>> kubernetesDeployments() {
        try {
            Map<String, Object> response = kubernetes.resource("deployments", "all");
            Object data = response.get("data");
            if (data instanceof Map<?, ?> map) {
                Object items = map.get("items");
                return mapList(items);
            }
        } catch (Exception ignored) {
            // Returned as explicit per-component KUBERNETES_CONTEXT_UNAVAILABLE_OR_NO_DEPLOYMENTS state.
        }
        return List.of();
    }

    private List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rows.add((Map<String, Object>) map);
                }
            }
        }
        return rows;
    }

    private List<Map<String, Object>> filter(List<Map<String, Object>> rows, String type) {
        return rows.stream().filter(row -> type.equals(row.get("componentType"))).toList();
    }

    private List<Map<String, Object>> dependencyEdges(List<Map<String, Object>> rows) {
        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object dependencies = row.get("dependencies");
            if (dependencies instanceof List<?> list) {
                for (Object dependency : list) {
                    Map<String, Object> edge = new LinkedHashMap<>();
                    edge.put("source", row.get("id"));
                    edge.put("target", dependency);
                    edge.put("sourceType", row.get("componentType"));
                    edge.put("sourceGroup", row.get("serviceGroup"));
                    edges.add(edge);
                }
            }
        }
        return edges;
    }

    private List<Map<String, Object>> openApiLinks(List<Map<String, Object>> rows) {
        List<Map<String, Object>> links = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String openApi = str(row.get("openApi"));
            if (openApi.isBlank()) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("name", row.get("name"));
            item.put("group", row.get("serviceGroup"));
            item.put("openApi", openApi);
            item.put("live", row.get("live"));
            item.put("state", row.get("state"));
            links.add(item);
        }
        return links;
    }

    private Map<String, Object> summary(List<Map<String, Object>> rows) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("components", rows.size());
        summary.put("live", rows.stream().filter(row -> Boolean.TRUE.equals(row.get("live"))).count());
        summary.put("unavailable", rows.stream().filter(row -> !Boolean.TRUE.equals(row.get("live"))).count());
        summary.put("microFrontends", rows.stream().filter(row -> "MICRO_FRONTEND".equals(row.get("componentType"))).count());
        summary.put("backendServices", rows.stream().filter(row -> "BACKEND_SERVICE".equals(row.get("componentType"))).count());
        summary.put("infrastructure", rows.stream().filter(row -> "INFRASTRUCTURE".equals(row.get("componentType"))).count());
        return summary;
    }

    private Map<String, Object> base(String source) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", source);
        out.put("executedAt", Instant.now().toString());
        out.put("realDataOnly", true);
        out.put("live", true);
        return out;
    }

    private Map asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map) map : Map.of();
    }

    private String joinUrl(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) return path == null ? "" : path;
        if (path == null || path.isBlank()) return baseUrl;
        return baseUrl.replaceAll("/+$", "") + (path.startsWith("/") ? path : "/" + path);
    }

    private String gatewayUrl() {
        return env("GATEWAY_SERVICE_URL", "http://gateway-service:8080");
    }

    private String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private String str(Object value) {
        return Objects.toString(value, "");
    }

    private String trim(String value) {
        if (value == null) return "";
        return value.length() > 1200 ? value.substring(0, 1200) + "…" : value;
    }

    private String firstContainerName(Map<String, Object> raw) {
        Object names = raw.get("Names");
        if (names instanceof List<?> list && !list.isEmpty()) return String.valueOf(list.get(0)).replaceFirst("^/+", "");
        String id = str(raw.get("Id"));
        return id.length() > 12 ? id.substring(0, 12) : id;
    }
}
