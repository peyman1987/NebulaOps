package dev.nebulaops.gateway.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.nebulaops.gateway.client.DockerSocketClient;
import dev.nebulaops.gateway.client.ToolResult;
import dev.nebulaops.gateway.service.DockerRuntimeService;
import dev.nebulaops.gateway.service.KubernetesPlatformService;
import dev.nebulaops.gateway.service.PlatformEventPublisher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * NebulaOps v24.1 — Safe Action Plan gate.
 *
 * Destructive or disruptive operations are planned first from live Docker,
 * kubectl and Helm evidence. The UI calls the plan endpoints, displays the
 * impact/risk/reversibility/command, and then calls /api/platform/actions/execute
 * only when the user typed the exact confirmation phrase. No plan contains
 * synthetic runtime rows: empty/unavailable sources are returned explicitly.
 */
@RestController
@SuppressWarnings({"unchecked", "rawtypes"})
public class PlatformSafeActionsController {

    private static final String DOCKER_SOCKET = "/var/run/docker.sock";

    private final DockerSocketClient dockerSocket;
    private final DockerRuntimeService dockerRuntime;
    private final KubernetesPlatformService kubernetes;
    private final PlatformEventPublisher events;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlatformSafeActionsController(DockerSocketClient dockerSocket,
                                         DockerRuntimeService dockerRuntime,
                                         KubernetesPlatformService kubernetes,
                                         PlatformEventPublisher events) {
        this.dockerSocket = dockerSocket;
        this.dockerRuntime = dockerRuntime;
        this.kubernetes = kubernetes;
        this.events = events;
    }

    @PostMapping("/api/runtime/docker/actions/plan")
    public Map<String, Object> dockerActionPlan(@RequestBody(required = false) Map<String, Object> body) {
        return dockerPlan(body == null ? Collections.emptyMap() : body);
    }

    @PostMapping("/api/kubernetes/actions/plan")
    public Map<String, Object> kubernetesActionPlan(@RequestBody(required = false) Map<String, Object> body,
                                                    @RequestParam(required = false) String clusterId) {
        Map<String, Object> input = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        if (clusterId != null && !clusterId.isBlank()) input.put("clusterId", clusterId);
        return kubernetesPlan(input);
    }

    @PostMapping("/api/platform/actions/execute")
    public Map<String, Object> executePlannedAction(@RequestBody(required = false) Map<String, Object> body,
                                                    @RequestParam(required = false) String clusterId) {
        Map<String, Object> request = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        if (clusterId != null && !clusterId.isBlank()) request.put("clusterId", clusterId);

        Map<String, Object> planPayload = planPayload(request);
        Map<String, Object> plan = asMap(planPayload.get("plan"));
        if (!Boolean.TRUE.equals(planPayload.get("ok")) || plan.isEmpty()) {
            Map<String, Object> out = base("safe-action-execute");
            out.put("ok", false);
            out.put("state", "PLAN_UNAVAILABLE");
            out.put("error", firstNonBlank(str(planPayload.get("error")), "The action could not be planned from current live runtime evidence."));
            out.put("plan", planPayload);
            return out;
        }

        String expected = str(plan.get("confirmationPhrase"));
        String actual = firstNonBlank(str(request.get("confirmation")), str(request.get("confirmationPhrase")), str(request.get("typedConfirmation")));
        if (expected.isBlank() || !Objects.equals(expected, actual)) {
            Map<String, Object> out = base("safe-action-execute");
            out.put("ok", false);
            out.put("state", "CONFIRMATION_REQUIRED");
            out.put("message", "Execution blocked. Type the exact confirmationPhrase returned by the plan endpoint.");
            out.put("confirmationPhrase", expected);
            out.put("plan", plan);
            return out;
        }
        if (Boolean.TRUE.equals(plan.get("blocked"))) {
            Map<String, Object> out = base("safe-action-execute");
            out.put("ok", false);
            out.put("state", "PLAN_BLOCKED");
            out.put("message", "Execution blocked by the current safe-action plan.");
            out.put("blockers", plan.getOrDefault("blockers", List.of()));
            out.put("plan", plan);
            return out;
        }

        String domain = str(plan.get("domain"));
        if (domain.equals("docker")) return executeDocker(plan, request);
        if (domain.equals("kubernetes") || domain.equals("helm")) return executeKubernetes(plan, request);

        Map<String, Object> out = base("safe-action-execute");
        out.put("ok", false);
        out.put("state", "UNSUPPORTED_DOMAIN");
        out.put("plan", plan);
        return out;
    }

    private Map<String, Object> planPayload(Map<String, Object> request) {
        Object embedded = request.get("plan");
        if (embedded instanceof Map<?, ?> m) {
            Map<String, Object> out = base("safe-action-plan");
            out.put("ok", true);
            out.put("plan", new LinkedHashMap<>((Map<String, Object>) m));
            return out;
        }
        String domain = firstNonBlank(str(request.get("domain")), str(request.get("provider")), str(request.get("runtime"))).toLowerCase(Locale.ROOT);
        String action = str(request.get("action")).toLowerCase(Locale.ROOT);
        if (domain.contains("docker") || action.startsWith("docker.") || action.startsWith("container.") || action.startsWith("project.") || action.contains("prune")) {
            return dockerPlan(request);
        }
        return kubernetesPlan(request);
    }

    private Map<String, Object> dockerPlan(Map<String, Object> body) {
        String action = normalizeDockerAction(str(body.get("action")));
        String target = normalizeTarget(body);
        Map params = asMap(body.get("parameters"));
        Map<String, Object> status = dockerSocket.status();
        Map<String, Object> out = base("docker-safe-action-plan");
        if (!Boolean.TRUE.equals(status.get("ok"))) {
            out.put("ok", false);
            out.put("state", "DOCKER_UNAVAILABLE");
            out.put("toolStatus", status);
            out.put("error", firstNonBlank(str(status.get("message")), "Docker Engine API is unavailable."));
            return out;
        }
        if (action.isBlank()) {
            out.put("ok", false);
            out.put("state", "ACTION_REQUIRED");
            out.put("error", "action is required");
            return out;
        }

        List<Map<String, Object>> impacted = dockerImpactedResources(action, target);
        Map<String, Object> plan = commonPlan("docker", action, target, params);
        plan.put("risk", dockerRisk(action, impacted, params));
        plan.put("reversible", dockerReversible(action));
        plan.put("reversibility", dockerReversibility(action));
        plan.put("apiCall", dockerApiCall(action, target, params));
        plan.put("command", dockerCommand(action, target, params));
        plan.put("impactedResources", impacted);
        plan.put("dependencies", dockerDependencies(action, target, impacted));
        plan.put("whatWillBeTouched", dockerTouched(action, impacted));
        plan.put("blocked", dockerBlocked(action, target, impacted));
        plan.put("blockers", dockerBlockers(action, target, impacted));
        plan.put("confirmationPhrase", confirmationPhrase("docker", action, target));
        plan.put("executionEndpoint", "/api/platform/actions/execute");
        plan.put("executePayload", Map.of("domain", "docker", "action", action, "target", target, "parameters", params));
        plan.put("realDataOnly", true);
        plan.put("planId", stableId(plan));

        out.put("ok", true);
        out.put("plan", plan);
        out.put("items", impacted);
        out.put("count", impacted.size());
        out.put("toolStatus", status);
        return out;
    }

    private Map<String, Object> kubernetesPlan(Map<String, Object> body) {
        String action = normalizeKubernetesAction(str(body.get("action")));
        String clusterId = str(body.get("clusterId"));
        String namespace = firstNonBlank(str(body.get("namespace")), str(body.get("ns")), namespaceFromTarget(str(body.get("target"))), "default");
        String name = firstNonBlank(str(body.get("name")), nameFromTarget(str(body.get("target"))), str(body.get("target")));
        String kind = firstNonBlank(str(body.get("kind")), kindFromAction(action));
        Map params = asMap(body.get("parameters"));
        Map<String, Object> out = base("kubernetes-safe-action-plan");
        if (action.isBlank()) {
            out.put("ok", false);
            out.put("state", "ACTION_REQUIRED");
            out.put("error", "action is required");
            return out;
        }

        List<Map<String, Object>> impacted = kubernetesImpactedResources(action, kind, namespace, name, body, clusterId);
        ToolResult auth = kubernetes.runKubectl(kubernetesAuthCheckCommand(action, kind, namespace), clusterId, 12);
        Map<String, Object> plan = commonPlan(action.startsWith("helm.") ? "helm" : "kubernetes", action, targetName(namespace, name, body), params);
        plan.put("kind", kind);
        plan.put("namespace", namespace);
        plan.put("name", name);
        plan.put("clusterId", clusterId.isBlank() ? "current-context" : clusterId);
        plan.put("risk", kubernetesRisk(action, impacted, params));
        plan.put("reversible", kubernetesReversible(action));
        plan.put("reversibility", kubernetesReversibility(action));
        plan.put("command", kubernetesCommand(action, kind, namespace, name, body, params));
        plan.put("apiCall", kubernetesApiCall(action, kind, namespace, name));
        plan.put("impactedResources", impacted);
        plan.put("dependencies", kubernetesDependencies(action, kind, namespace, name, impacted, clusterId));
        plan.put("whatWillBeTouched", kubernetesTouched(action, kind, namespace, name, impacted));
        plan.put("authorizationCheck", auth.asMap());
        plan.put("blocked", !auth.ok() && actionRequiresAuthorization(action));
        plan.put("blockers", !auth.ok() && actionRequiresAuthorization(action) ? List.of(firstNonBlank(auth.stderr(), auth.stdout(), "kubectl auth check failed")) : List.of());
        plan.put("confirmationPhrase", confirmationPhrase(action.startsWith("helm.") ? "helm" : "kubernetes", action, targetName(namespace, name, body)));
        plan.put("executionEndpoint", "/api/platform/actions/execute");
        plan.put("executePayload", Map.of("domain", action.startsWith("helm.") ? "helm" : "kubernetes", "action", action, "kind", kind, "namespace", namespace, "name", name, "target", targetName(namespace, name, body), "parameters", params, "clusterId", clusterId));
        plan.put("realDataOnly", true);
        plan.put("planId", stableId(plan));

        out.put("ok", true);
        out.put("plan", plan);
        out.put("items", impacted);
        out.put("count", impacted.size());
        return out;
    }

    private Map<String, Object> executeDocker(Map<String, Object> plan, Map<String, Object> request) {
        String action = str(plan.get("action"));
        String target = str(plan.get("target"));
        Map params = asMap(firstNonNull(request.get("parameters"), plan.get("parameters")));
        Map<String, Object> out = base("safe-action-execute");
        out.put("domain", "docker");
        out.put("action", action);
        out.put("target", target);
        out.put("plan", plan);
        try {
            if (action.equals("project.stop") || action.equals("project.restart")) {
                List<Map<String, Object>> results = new ArrayList<>();
                boolean ok = true;
                String operation = action.equals("project.stop") ? "stop?t=" + intValue(params.get("timeout"), 10) : "restart?t=" + intValue(params.get("timeout"), 5);
                for (Map<String, Object> row : containersForProject(target)) {
                    String id = str(row.get("id"));
                    if (id.isBlank()) continue;
                    HttpSocketResponse response = dockerRequest("POST", "/containers/" + safeDocker(id) + "/" + operation, null);
                    boolean rowOk = response.status >= 200 && response.status < 300;
                    ok = ok && rowOk;
                    results.add(Map.of("id", id, "name", str(row.get("name")), "status", response.status, "ok", rowOk, "body", parseMaybeJson(response.body)));
                }
                out.put("ok", ok);
                out.put("live", true);
                out.put("items", results);
                out.put("count", results.size());
                out.put("correlationId", events.mutation("SAFE_DOCKER_" + safeEvent(action), target, ok, out));
                return out;
            }
            HttpSocketResponse response = dockerRequest(dockerMethod(action), dockerPath(action, target, params), dockerBody(action));
            boolean ok = response.status >= 200 && response.status < 300;
            out.put("ok", ok);
            out.put("live", true);
            out.put("status", response.status);
            out.put("body", parseMaybeJson(response.body));
            out.put("correlationId", events.mutation("SAFE_DOCKER_" + safeEvent(action), target, ok, out));
            return out;
        } catch (Exception e) {
            out.put("ok", false);
            out.put("live", false);
            out.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            out.put("correlationId", events.mutation("SAFE_DOCKER_" + safeEvent(action), target, false, out));
            return out;
        }
    }

    private Map<String, Object> executeKubernetes(Map<String, Object> plan, Map<String, Object> request) {
        String action = str(plan.get("action"));
        String kind = firstNonBlank(str(plan.get("kind")), str(request.get("kind")), kindFromAction(action));
        String namespace = firstNonBlank(str(plan.get("namespace")), str(request.get("namespace")), "default");
        String name = firstNonBlank(str(plan.get("name")), str(request.get("name")), nameFromTarget(str(plan.get("target"))));
        String clusterId = str(plan.get("clusterId"));
        Map params = asMap(firstNonNull(request.get("parameters"), plan.get("parameters")));
        Map<String, Object> out = base("safe-action-execute");
        out.put("domain", action.startsWith("helm.") ? "helm" : "kubernetes");
        out.put("action", action);
        out.put("target", str(plan.get("target")));
        out.put("plan", plan);
        try {
            ToolResult r;
            if (action.equals("yaml.apply") || action.equals("yaml.delete")) {
                String yaml = firstNonBlank(str(request.get("yaml")), str(asMap(request.get("parameters")).get("yaml")), str(params.get("yaml")));
                if (yaml.isBlank()) return executionError(out, "yaml required");
                Path tmp = Files.createTempFile("nebula-safe-action-", ".yaml");
                Files.writeString(tmp, yaml);
                String verb = action.equals("yaml.delete") ? "delete" : "apply";
                r = kubernetes.runKubectl("kubectl " + verb + " -f " + tmp.toAbsolutePath(), clusterId, 60);
                Files.deleteIfExists(tmp);
            } else {
                r = kubernetes.runKubectl(kubernetesCommand(action, kind, namespace, name, request, params), clusterId, kubernetesTimeout(action));
            }
            boolean ok = r.ok();
            out.put("ok", ok);
            out.put("live", true);
            out.put("stdout", r.stdout());
            out.put("stderr", r.stderr());
            out.put("exitCode", r.exitCode());
            out.put("durationMs", r.durationMs());
            out.put("toolStatus", r.asMap());
            out.put("correlationId", events.mutation("SAFE_KUBERNETES_" + safeEvent(action), str(plan.get("target")), ok, out));
            return out;
        } catch (Exception e) {
            return executionError(out, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private Map<String, Object> executionError(Map<String, Object> out, String message) {
        out.put("ok", false);
        out.put("live", false);
        out.put("error", message);
        out.put("correlationId", events.mutation("SAFE_ACTION_FAILED", str(out.get("target")), false, out));
        return out;
    }

    private Map<String, Object> commonPlan(String domain, String action, String target, Map params) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("domain", domain);
        plan.put("action", action);
        plan.put("target", target);
        plan.put("parameters", params == null ? Map.of() : params);
        plan.put("requiresConfirmation", true);
        plan.put("generatedAt", Instant.now().toString());
        plan.put("status", "PLAN_READY");
        plan.put("safeActionPlan", true);
        return plan;
    }

    private List<Map<String, Object>> dockerImpactedResources(String action, String target) {
        if (action.startsWith("project.")) return containersForProject(target);
        if (action.contains("prune")) return pruneCandidates(action);
        if (target.isBlank()) return List.of();
        try {
            Map<String, Object> inspect = dockerJsonMap("/containers/" + safeDocker(target) + "/json");
            if (!inspect.containsKey("error")) return List.of(dockerContainerImpact(inspect));
        } catch (Exception ignored) { }
        return dockerSocket.containers().stream()
                .filter(c -> target.equals(str(c.get("Id"))) || target.equals(firstContainerName(c)) || str(c.get("Id")).startsWith(target))
                .map(this::dockerContainerSummaryImpact)
                .toList();
    }

    private List<Map<String, Object>> containersForProject(String project) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : dockerSocket.containers()) {
            Map labels = asMap(c.get("Labels"));
            String compose = firstNonBlank(str(labels.get("com.docker.compose.project")), str(labels.get("com.docker.stack.namespace")));
            if (project.equals(compose) || ("standalone".equals(project) && compose.isBlank())) out.add(dockerContainerSummaryImpact(c));
        }
        out.sort(Comparator.comparing(r -> str(r.get("name"))));
        return out;
    }

    private List<Map<String, Object>> pruneCandidates(String action) {
        Map<String, Object> candidates = dockerRuntime.unusedResourceCandidates();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : items(candidates)) {
            String type = str(row.get("type"));
            if (action.equals("system.prune") || action.equals(type + ".prune") || (action.equals("images.prune") && type.equals("image")) || (action.equals("volumes.prune") && type.equals("volume")) || (action.equals("networks.prune") && type.equals("network"))) {
                rows.add(row);
            }
        }
        return rows;
    }

    private Map<String, Object> dockerContainerImpact(Map<String, Object> inspect) {
        Map<String, Object> config = asMap(inspect.get("Config"));
        Map<String, Object> state = asMap(inspect.get("State"));
        Map<String, Object> labels = asMap(config.get("Labels"));
        List<Map<String, Object>> mounts = new ArrayList<>();
        Object rawMounts = inspect.get("Mounts");
        if (rawMounts instanceof List<?> list) for (Object item : list) if (item instanceof Map<?, ?> m) mounts.add(new LinkedHashMap<>((Map<String, Object>) m));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "container");
        row.put("id", str(inspect.get("Id")));
        row.put("name", str(inspect.get("Name")).replaceFirst("^/+", ""));
        row.put("image", firstNonBlank(str(config.get("Image")), str(inspect.get("Image"))));
        row.put("state", firstNonBlank(str(state.get("Status")), str(state.get("Running"))));
        row.put("restartCount", inspect.getOrDefault("RestartCount", 0));
        row.put("project", firstNonBlank(str(labels.get("com.docker.compose.project")), str(labels.get("com.docker.stack.namespace"))));
        row.put("service", str(labels.get("com.docker.compose.service")));
        row.put("mounts", mounts);
        row.put("source", "docker-engine-api");
        return row;
    }

    private Map<String, Object> dockerContainerSummaryImpact(Map<String, Object> c) {
        Map labels = asMap(c.get("Labels"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "container");
        row.put("id", str(c.get("Id")));
        row.put("name", firstContainerName(c));
        row.put("image", str(c.get("Image")));
        row.put("state", str(c.get("State")));
        row.put("status", str(c.get("Status")));
        row.put("project", firstNonBlank(str(labels.get("com.docker.compose.project")), str(labels.get("com.docker.stack.namespace"))));
        row.put("service", str(labels.get("com.docker.compose.service")));
        row.put("source", "docker-engine-api");
        return row;
    }

    private List<Map<String, Object>> dockerDependencies(String action, String target, List<Map<String, Object>> impacted) {
        List<Map<String, Object>> deps = new ArrayList<>();
        for (Map<String, Object> row : impacted) {
            if (!str(row.get("project")).isBlank()) deps.add(Map.of("type", "compose-project", "name", row.get("project"), "source", "docker-labels"));
            if (!str(row.get("service")).isBlank()) deps.add(Map.of("type", "compose-service", "name", row.get("service"), "source", "docker-labels"));
            Object mounts = row.get("mounts");
            if (mounts instanceof List<?> list && !list.isEmpty()) deps.add(Map.of("type", "mounts", "count", list.size(), "source", "docker-inspect"));
        }
        return deps;
    }

    private List<String> dockerTouched(String action, List<Map<String, Object>> impacted) {
        List<String> out = new ArrayList<>();
        if (action.contains("prune")) out.add("Docker unused resource candidates returned by /api/runtime/docker/prune/preview.");
        if (action.startsWith("project.")) out.add("All live Docker containers with matching Compose/Stack project labels.");
        if (action.startsWith("container.")) out.add("The selected Docker container and its runtime state from Docker inspect.");
        out.add("Docker Engine API endpoint: " + dockerApiCall(action, str(impacted.isEmpty() ? "" : impacted.get(0).get("id")), Map.of()));
        return out;
    }

    private boolean dockerBlocked(String action, String target, List<Map<String, Object>> impacted) {
        if (!action.contains("prune") && target.isBlank()) return true;
        return false;
    }

    private List<String> dockerBlockers(String action, String target, List<Map<String, Object>> impacted) {
        if (!action.contains("prune") && target.isBlank()) return List.of("target is required");
        return List.of();
    }

    private List<Map<String, Object>> kubernetesImpactedResources(String action, String kind, String ns, String name, Map<String, Object> body, String clusterId) {
        List<Map<String, Object>> impacted = new ArrayList<>();
        if (action.equals("yaml.apply") || action.equals("yaml.delete")) {
            String yaml = firstNonBlank(str(body.get("yaml")), str(asMap(body.get("parameters")).get("yaml")));
            ToolResult dry = yaml.isBlank() ? toolError("yaml is required") : yamlDryRun(action, yaml, clusterId);
            impacted.add(Map.of("type", "yaml-manifest", "name", "manifest", "source", "kubectl server dry-run", "toolStatus", dry.asMap()));
            return impacted;
        }
        if (action.startsWith("helm.")) {
            ToolResult status = kubernetes.runKubectl("helm status " + safe(name) + " -n " + safe(ns) + " -o json", clusterId, 20);
            impacted.add(Map.of("type", "helm-release", "namespace", ns, "name", name, "source", "helm status", "toolStatus", status.asMap(), "stdout", str(status.stdout()), "stderr", str(status.stderr())));
            return impacted;
        }
        String commandKind = safeKind(kind);
        if (!name.isBlank() && !commandKind.isBlank()) {
            ToolResult json = kubernetes.runKubectl("kubectl get " + commandKind + " " + safe(name) + (needsNamespace(commandKind) ? " -n " + safe(ns) : "") + " -o json", clusterId, 15);
            impacted.add(Map.of("type", commandKind, "namespace", ns, "name", name, "source", "kubectl get", "toolStatus", json.asMap(), "stdout", truncate(str(json.stdout()), 8000), "stderr", str(json.stderr())));
        }
        if (action.equals("workload.restart") || action.equals("workload.scale")) {
            ToolResult pods = kubernetes.runKubectl("kubectl get pods -n " + safe(ns) + " -o json", clusterId, 15);
            impacted.add(Map.of("type", "pods-in-namespace", "namespace", ns, "source", "kubectl get pods", "toolStatus", pods.asMap(), "stdout", truncate(str(pods.stdout()), 8000), "stderr", str(pods.stderr())));
        }
        return impacted;
    }

    private ToolResult yamlDryRun(String action, String yaml, String clusterId) {
        try {
            Path tmp = Files.createTempFile("nebula-safe-plan-", ".yaml");
            Files.writeString(tmp, yaml);
            String verb = action.equals("yaml.delete") ? "delete" : "apply";
            ToolResult r = kubernetes.runKubectl("kubectl " + verb + " --dry-run=server -f " + tmp.toAbsolutePath(), clusterId, 30);
            Files.deleteIfExists(tmp);
            return r;
        } catch (Exception e) {
            return toolError(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private List<Map<String, Object>> kubernetesDependencies(String action, String kind, String ns, String name, List<Map<String, Object>> impacted, String clusterId) {
        List<Map<String, Object>> deps = new ArrayList<>();
        if (action.contains("pod") || action.contains("workload")) {
            ToolResult events = kubernetes.runKubectl("kubectl get events -n " + safe(ns) + " --field-selector involvedObject.name=" + safe(name) + " -o json", clusterId, 12);
            deps.add(Map.of("type", "events", "namespace", ns, "name", name, "toolStatus", events.asMap(), "stdout", truncate(str(events.stdout()), 4000), "stderr", str(events.stderr())));
        }
        if (action.startsWith("helm.")) {
            ToolResult history = kubernetes.runKubectl("helm history " + safe(name) + " -n " + safe(ns) + " -o json", clusterId, 20);
            deps.add(Map.of("type", "helm-history", "namespace", ns, "name", name, "toolStatus", history.asMap(), "stdout", truncate(str(history.stdout()), 5000), "stderr", str(history.stderr())));
        }
        return deps;
    }

    private List<String> kubernetesTouched(String action, String kind, String ns, String name, List<Map<String, Object>> impacted) {
        List<String> out = new ArrayList<>();
        if (action.equals("pod.delete")) out.add("The selected pod will be deleted; its owner may create a replacement pod.");
        else if (action.equals("pod.restart")) out.add("The pod will be deleted or its owning workload will be restarted depending on ownerReferences.");
        else if (action.equals("workload.restart")) out.add("The selected workload rollout will be restarted and owned pods may be recreated.");
        else if (action.equals("workload.scale")) out.add("The selected workload replica count will be changed.");
        else if (action.startsWith("helm.")) out.add("The selected Helm release and the Kubernetes resources managed by it may be changed.");
        else if (action.startsWith("yaml.")) out.add("Resources contained in the submitted manifest will be applied or deleted.");
        else out.add("The selected Kubernetes resource will be changed by the planned command.");
        out.add("Command: " + kubernetesCommand(action, kind, ns, name, Map.of(), Map.of()));
        return out;
    }

    private ToolResult toolError(String message) {
        return new ToolResult(false, 1, message, "", message, 0L, Instant.now().toString());
    }

    private String kubernetesAuthCheckCommand(String action, String kind, String ns) {
        String verb = "get";
        if (action.contains("delete") || action.equals("helm.uninstall") || action.equals("yaml.delete")) verb = "delete";
        else if (action.contains("scale") || action.contains("restart") || action.contains("rollback") || action.contains("apply") || action.contains("cordon") || action.contains("drain")) verb = "patch";
        String resource = safeKind(firstNonBlank(kind, kindFromAction(action)));
        if (action.startsWith("helm.")) resource = "secrets";
        if (resource.isBlank()) resource = "pods";
        return "kubectl auth can-i " + verb + " " + resource + (needsNamespace(resource) ? " -n " + safe(ns) : "");
    }

    private boolean actionRequiresAuthorization(String action) {
        return !(action.equals("yaml.apply") || action.equals("yaml.delete"));
    }

    private String kubernetesCommand(String action, String kind, String ns, String name, Map body, Map params) {
        int replicas = intValue(firstNonNull(params.get("replicas"), body.get("replicas"), body.get("replicaCount")), 1);
        int revision = intValue(firstNonNull(params.get("revision"), body.get("revision")), 0);
        String resource = safeKind(firstNonBlank(kind, kindFromAction(action)));
        return switch (action) {
            case "pod.delete" -> "kubectl delete pod " + safe(name) + " -n " + safe(ns) + (truthy(params.get("force")) ? " --grace-period=0 --force" : "");
            case "pod.restart" -> "kubectl delete pod " + safe(name) + " -n " + safe(ns);
            case "workload.restart" -> "kubectl rollout restart " + workloadRef(resource, name) + " -n " + safe(ns);
            case "workload.scale" -> "kubectl scale " + workloadRef(resource, name) + " -n " + safe(ns) + " --replicas=" + Math.max(0, Math.min(replicas, 50));
            case "job.delete" -> "kubectl delete job " + safe(name) + " -n " + safe(ns);
            case "node.cordon" -> "kubectl cordon " + safe(name);
            case "node.drain" -> "kubectl drain " + safe(name) + " --ignore-daemonsets --delete-emptydir-data";
            case "helm.rollback" -> "helm rollback " + safe(name) + (revision > 0 ? " " + revision : "") + " -n " + safe(ns);
            case "helm.uninstall" -> "helm uninstall " + safe(name) + " -n " + safe(ns);
            case "yaml.apply" -> "kubectl apply -f <submitted-manifest>";
            case "yaml.delete" -> "kubectl delete -f <submitted-manifest>";
            default -> "kubectl get " + safe(resource) + " " + safe(name) + (needsNamespace(resource) ? " -n " + safe(ns) : "");
        };
    }

    private String kubernetesApiCall(String action, String kind, String ns, String name) {
        String k = safeKind(firstNonBlank(kind, kindFromAction(action)));
        return switch (action) {
            case "pod.delete" -> "DELETE /api/kubernetes/pods/" + ns + "/" + name;
            case "pod.restart" -> "POST /api/kubernetes/pods/" + ns + "/" + name + "/restart";
            case "workload.restart" -> "POST /api/kubernetes/" + plural(k) + "/" + ns + "/" + name + "/restart";
            case "workload.scale" -> "POST /api/kubernetes/" + plural(k) + "/" + ns + "/" + name + "/scale";
            case "helm.rollback" -> "POST /api/kubernetes/helm/releases/" + ns + "/" + name + "/rollback";
            case "helm.uninstall" -> "DELETE /api/kubernetes/helm/releases/" + ns + "/" + name;
            case "yaml.apply" -> "POST /api/kubernetes/apply";
            case "yaml.delete" -> "POST /api/kubernetes/delete";
            default -> "POST /api/platform/actions/execute";
        };
    }

    private String dockerPath(String action, String target, Map params) {
        String id = safeDocker(target);
        return switch (action) {
            case "container.stop" -> "/containers/" + id + "/stop?t=" + intValue(params.get("timeout"), 10);
            case "container.restart" -> "/containers/" + id + "/restart?t=" + intValue(params.get("timeout"), 5);
            case "container.kill" -> "/containers/" + id + "/kill?signal=" + safeDocker(firstNonBlank(str(params.get("signal")), "SIGKILL"));
            case "container.remove" -> "/containers/" + id + "?force=" + truthy(params.get("force")) + "&v=" + truthy(params.get("volumes"));
            case "images.prune" -> "/images/prune";
            case "volumes.prune" -> "/volumes/prune";
            case "networks.prune" -> "/networks/prune";
            case "system.prune" -> "/system/prune?volumes=" + truthy(params.get("volumes"));
            default -> "/containers/" + id + "/json";
        };
    }

    private String dockerMethod(String action) {
        if (action.equals("container.remove")) return "DELETE";
        return "POST";
    }

    private String dockerBody(String action) {
        return action.contains("prune") ? "{}" : null;
    }

    private String dockerApiCall(String action, String target, Map params) {
        if (action.equals("project.stop")) return "POST /api/runtime/docker/projects/" + safeDocker(target) + "/stop";
        if (action.equals("project.restart")) return "POST /api/runtime/docker/projects/" + safeDocker(target) + "/restart";
        return dockerMethod(action) + " " + dockerPath(action, target, params == null ? Map.of() : params);
    }

    private String dockerCommand(String action, String target, Map params) {
        return switch (action) {
            case "container.stop" -> "docker stop " + safeDocker(target);
            case "container.restart" -> "docker restart " + safeDocker(target);
            case "container.kill" -> "docker kill --signal " + safeDocker(firstNonBlank(str(params.get("signal")), "SIGKILL")) + " " + safeDocker(target);
            case "container.remove" -> "docker rm" + (truthy(params.get("force")) ? " --force" : "") + (truthy(params.get("volumes")) ? " --volumes" : "") + " " + safeDocker(target);
            case "project.stop" -> "docker compose --project-name " + safeDocker(target) + " stop";
            case "project.restart" -> "docker compose --project-name " + safeDocker(target) + " restart";
            case "images.prune" -> "docker image prune";
            case "volumes.prune" -> "docker volume prune";
            case "networks.prune" -> "docker network prune";
            case "system.prune" -> "docker system prune" + (truthy(params.get("volumes")) ? " --volumes" : "");
            default -> "docker action " + action + " " + safeDocker(target);
        };
    }

    private String dockerRisk(String action, List<Map<String, Object>> impacted, Map params) {
        if (action.equals("container.remove") || action.equals("container.kill") || action.equals("volumes.prune") || action.equals("system.prune") || truthy(params.get("volumes"))) return "HIGH";
        if (action.startsWith("project.") && impacted.size() > 3) return "HIGH";
        if (action.startsWith("project.") || action.equals("container.stop") || action.equals("container.restart") || action.equals("images.prune") || action.equals("networks.prune")) return "MEDIUM";
        return "LOW";
    }

    private boolean dockerReversible(String action) {
        return action.equals("container.stop") || action.equals("container.restart") || action.equals("project.stop") || action.equals("project.restart");
    }

    private String dockerReversibility(String action) {
        return dockerReversible(action) ? "Potentially reversible by starting/restarting the same container or project if the original resources still exist." : "Not safely reversible from NebulaOps; removed/pruned resources may require rebuild, repull, recreate or restore.";
    }

    private String kubernetesRisk(String action, List<Map<String, Object>> impacted, Map params) {
        if (action.equals("helm.uninstall") || action.equals("node.drain") || action.equals("yaml.delete")) return "HIGH";
        if (action.equals("workload.scale") && intValue(params.get("replicas"), 1) == 0) return "HIGH";
        if (action.equals("pod.delete") || action.equals("pod.restart") || action.equals("workload.restart") || action.equals("helm.rollback") || action.equals("yaml.apply")) return "MEDIUM";
        return "LOW";
    }

    private boolean kubernetesReversible(String action) {
        return action.equals("workload.scale") || action.equals("workload.restart") || action.equals("pod.restart") || action.equals("helm.rollback") || action.equals("node.cordon");
    }

    private String kubernetesReversibility(String action) {
        if (action.equals("helm.rollback")) return "Reversible by performing another Helm rollback/upgrade if the release history remains available.";
        if (action.equals("workload.scale")) return "Reversible by scaling the workload back to its previous replica count if capacity and configuration allow it.";
        if (action.equals("pod.delete") || action.equals("pod.restart")) return "Usually reversible only if a controller recreates the pod; standalone pods may not recover automatically.";
        if (action.equals("yaml.delete") || action.equals("helm.uninstall")) return "Not safely reversible unless the manifest/chart values and persistent data are available for restore.";
        return kubernetesReversible(action) ? "Potentially reversible with a follow-up Kubernetes command." : "Not automatically reversible.";
    }

    private String normalizeDockerAction(String raw) {
        String a = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('_', '.').replace('-', '.');
        a = a.replace("docker.", "");
        return switch (a) {
            case "kill", "container.kill" -> "container.kill";
            case "remove", "rm", "delete", "container.remove", "container.delete" -> "container.remove";
            case "stop", "container.stop" -> "container.stop";
            case "restart", "container.restart" -> "container.restart";
            case "project.stop", "compose.stop" -> "project.stop";
            case "project.restart", "compose.restart" -> "project.restart";
            case "prune.images", "image.prune", "images.prune" -> "images.prune";
            case "prune.volumes", "volume.prune", "volumes.prune" -> "volumes.prune";
            case "prune.networks", "network.prune", "networks.prune" -> "networks.prune";
            case "prune.system", "system.prune", "docker.prune" -> "system.prune";
            default -> a;
        };
    }

    private String normalizeKubernetesAction(String raw) {
        String a = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('_', '.').replace('-', '.');
        a = a.replace("kubernetes.", "").replace("k8s.", "");
        return switch (a) {
            case "delete.pod", "pod.delete" -> "pod.delete";
            case "restart.pod", "pod.restart" -> "pod.restart";
            case "rollout.restart", "restart.workload", "workload.restart", "deployment.restart", "statefulset.restart", "daemonset.restart" -> "workload.restart";
            case "scale", "workload.scale", "deployment.scale", "statefulset.scale" -> "workload.scale";
            case "delete.job", "job.delete" -> "job.delete";
            case "helm.rollback", "rollback.helm" -> "helm.rollback";
            case "helm.uninstall", "uninstall.helm" -> "helm.uninstall";
            case "yaml.apply", "apply.yaml", "apply" -> "yaml.apply";
            case "yaml.delete", "delete.yaml" -> "yaml.delete";
            case "node.drain", "drain.node" -> "node.drain";
            case "node.cordon", "cordon.node" -> "node.cordon";
            default -> a;
        };
    }

    private String kindFromAction(String action) {
        if (action.startsWith("pod.")) return "pod";
        if (action.startsWith("helm.")) return "helmrelease";
        if (action.startsWith("node.")) return "node";
        if (action.startsWith("job.")) return "job";
        if (action.startsWith("yaml.")) return "manifest";
        return "deployment";
    }

    private String workloadRef(String kind, String name) {
        String k = safeKind(kind);
        if (k.equals("deployments")) k = "deployment";
        if (k.equals("statefulsets")) k = "statefulset";
        if (k.equals("daemonsets")) k = "daemonset";
        return k + "/" + safe(name);
    }

    private String safeKind(String kind) {
        String k = safe(firstNonBlank(kind, "deployment")).toLowerCase(Locale.ROOT);
        if (k.equals("deployments")) return "deployment";
        if (k.equals("statefulsets")) return "statefulset";
        if (k.equals("daemonsets")) return "daemonset";
        if (k.equals("pods")) return "pod";
        if (k.equals("jobs")) return "job";
        if (k.equals("nodes")) return "node";
        if (k.equals("services")) return "service";
        return k;
    }

    private String plural(String kind) {
        return switch (safeKind(kind)) {
            case "deployment" -> "deployments";
            case "statefulset" -> "statefulsets";
            case "daemonset" -> "daemonsets";
            case "pod" -> "pods";
            case "job" -> "jobs";
            default -> safeKind(kind) + "s";
        };
    }

    private boolean needsNamespace(String kind) {
        String k = safeKind(kind);
        return !(k.equals("node") || k.equals("namespace") || k.equals("persistentvolume") || k.equals("storageclass"));
    }

    private int kubernetesTimeout(String action) {
        if (action.startsWith("helm.")) return 90;
        if (action.equals("node.drain")) return 120;
        if (action.startsWith("yaml.")) return 60;
        return 45;
    }

    private HttpSocketResponse dockerRequest(String method, String path, String body) throws IOException {
        var address = UnixDomainSocketAddress.of(DOCKER_SOCKET);
        try (var channel = SocketChannel.open(address)) {
            byte[] bodyBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            StringBuilder req = new StringBuilder();
            req.append(method).append(' ').append(path).append(" HTTP/1.0\r\n");
            req.append("Host: localhost\r\n");
            req.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
            if (bodyBytes.length > 0) req.append("Content-Type: application/json\r\n");
            req.append("\r\n");
            channel.write(ByteBuffer.wrap(req.toString().getBytes(StandardCharsets.UTF_8)));
            if (bodyBytes.length > 0) channel.write(ByteBuffer.wrap(bodyBytes));
            String response = readAll(channel);
            int status = 200;
            if (response.startsWith("HTTP/")) {
                String firstLine = response.split("\r?\n", 2)[0];
                String[] parts = firstLine.split(" ");
                if (parts.length >= 2) status = Integer.parseInt(parts[1]);
            }
            int bodyStart = response.indexOf("\r\n\r\n");
            if (bodyStart < 0) bodyStart = response.indexOf("\n\n");
            String responseBody = bodyStart >= 0 ? response.substring(bodyStart + (response.charAt(bodyStart) == '\r' ? 4 : 2)).trim() : response;
            return new HttpSocketResponse(status, responseBody);
        }
    }

    private Map<String, Object> dockerJsonMap(String path) throws IOException {
        return mapper.readValue(dockerRequest("GET", path, null).body, new TypeReference<>() {});
    }

    private String readAll(SocketChannel channel) throws IOException {
        var sb = new StringBuilder();
        var buf = ByteBuffer.allocate(65536);
        while (channel.read(buf) != -1) {
            buf.flip();
            sb.append(StandardCharsets.UTF_8.decode(buf));
            buf.clear();
        }
        return sb.toString();
    }

    private Object parseMaybeJson(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try { return mapper.readValue(raw, Object.class); } catch (Exception ignored) { return raw; }
    }

    private Map<String, Object> base(String resource) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resource", resource);
        out.put("tool", "nebulaops-gateway");
        out.put("live", true);
        out.put("realDataOnly", true);
        out.put("generatedAt", Instant.now().toString());
        return out;
    }

    private String confirmationPhrase(String domain, String action, String target) {
        return "EXECUTE " + domain.toUpperCase(Locale.ROOT) + " " + action.toUpperCase(Locale.ROOT) + " " + firstNonBlank(target, "TARGET");
    }

    private String stableId(Map<String, Object> plan) {
        try {
            String raw = str(plan.get("domain")) + ":" + str(plan.get("action")) + ":" + str(plan.get("target")) + ":" + str(plan.get("command"));
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("safe-");
            for (int i = 0; i < Math.min(10, bytes.length); i++) sb.append(String.format("%02x", bytes[i]));
            return sb.toString();
        } catch (Exception e) {
            return "safe-" + Math.abs(plan.hashCode());
        }
    }

    private String normalizeTarget(Map<String, Object> body) {
        Object targetObj = body.get("target");
        if (targetObj instanceof Map<?, ?> m) return firstNonBlank(str(m.get("id")), str(m.get("name")), str(m.get("target")));
        return firstNonBlank(str(body.get("containerId")), str(body.get("id")), str(body.get("project")), str(body.get("name")), str(targetObj));
    }

    private String targetName(String ns, String name, Map body) {
        String explicit = str(body.get("target"));
        if (!explicit.isBlank() && !(body.get("target") instanceof Map<?, ?>)) return explicit;
        if (name == null || name.isBlank()) return "manifest";
        return (ns == null || ns.isBlank() ? "default" : ns) + "/" + name;
    }

    private String namespaceFromTarget(String target) {
        if (target == null || !target.contains("/")) return "";
        return target.substring(0, target.indexOf('/'));
    }

    private String nameFromTarget(String target) {
        if (target == null) return "";
        return target.contains("/") ? target.substring(target.indexOf('/') + 1) : target;
    }

    private List<Map<String, Object>> items(Map<String, Object> payload) {
        Object rows = payload == null ? null : payload.get("items");
        if (!(rows instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object row : list) if (row instanceof Map<?, ?> m) out.add(new LinkedHashMap<>((Map<String, Object>) m));
        return out;
    }

    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>();
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private String firstContainerName(Map<String, Object> raw) {
        Object names = raw.get("Names");
        if (names instanceof List<?> list && !list.isEmpty()) return str(list.get(0)).replaceFirst("^/+", "");
        String id = str(raw.get("Id"));
        return id.length() > 12 ? id.substring(0, 12) : id;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean b) return b;
        String text = str(value).toLowerCase(Locale.ROOT);
        return text.equals("true") || text.equals("1") || text.equals("yes") || text.equals("y");
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(str(value)); } catch (Exception ignored) { return fallback; }
    }

    private String safe(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9._:\\-]", "");
    }

    private String safeDocker(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9_.:@\\-]", "");
    }

    private String safeEvent(String value) {
        return safe(value).replace('.', '_').toUpperCase(Locale.ROOT);
    }

    private String str(Object value) { return value == null ? "" : String.valueOf(value); }

    private String truncate(String text, int limit) {
        if (text == null) return "";
        return text.length() <= limit ? text : text.substring(0, limit) + "\n... truncated by safe-action plan ...";
    }

    private record HttpSocketResponse(int status, String body) {}
}
