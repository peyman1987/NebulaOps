package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.service.DockerRuntimeService;
import dev.nebulaops.gateway.service.KubernetesPlatformService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * NebulaOps v24.1 — Operational Issues dashboard API.
 *
 * Issues are derived only from live runtime sources: Docker Engine, kubectl,
 * Helm and the extension control-plane status cache. The controller does not
 * seed, store or return synthetic sample rows. When a source is unavailable the
 * response carries an explicit source health state and, where appropriate, a
 * source-unavailable issue backed by the real tool status.
 */
@RestController
@RequestMapping("/api/platform/issues")
@SuppressWarnings({"unchecked", "rawtypes"})
public class PlatformIssuesController {

    private final DockerRuntimeService docker;
    private final KubernetesPlatformService kubernetes;
    private final ObjectProvider<ExtensionControlController> extensionControl;

    public PlatformIssuesController(DockerRuntimeService docker,
                                    KubernetesPlatformService kubernetes,
                                    ObjectProvider<ExtensionControlController> extensionControl) {
        this.docker = docker;
        this.kubernetes = kubernetes;
        this.extensionControl = extensionControl;
    }

    @GetMapping({"", "/"})
    public Map<String, Object> issues(@RequestParam(defaultValue = "all") String namespace,
                                      @RequestParam(required = false) String clusterId,
                                      @RequestParam(required = false) String severity,
                                      @RequestParam(required = false) String source,
                                      @RequestParam(defaultValue = "250") int limit) {
        List<Map<String, Object>> rows = collectIssues(namespace, clusterId);
        rows = filter(rows, severity, source);
        rows.sort(Comparator
                .comparingInt((Map<String, Object> row) -> severityWeight(str(row.get("severity"))))
                .thenComparing(row -> str(row.getOrDefault("sourceType", "")))
                .thenComparing(row -> str(row.getOrDefault("name", ""))));
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        if (rows.size() > safeLimit) rows = new ArrayList<>(rows.subList(0, safeLimit));

        Map<String, Object> out = base("operational-issues", namespace, clusterId);
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("summary", summary(rows));
        out.put("sourceHealth", sourceHealth(namespace, clusterId));
        out.put("message", rows.isEmpty()
                ? "No live operational issues returned by the runtime sources."
                : "Operational issues derived from Docker Engine, Kubernetes, Helm and extension runtime status.");
        return out;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam(defaultValue = "all") String namespace,
                                       @RequestParam(required = false) String clusterId) {
        List<Map<String, Object>> rows = collectIssues(namespace, clusterId);
        Map<String, Object> out = base("operational-issues-summary", namespace, clusterId);
        out.put("summary", summary(rows));
        out.put("sourceHealth", sourceHealth(namespace, clusterId));
        out.put("items", summaryItems(rows));
        out.put("count", rows.size());
        return out;
    }

    @GetMapping("/{id}/evidence")
    public Map<String, Object> evidence(@PathVariable String id,
                                        @RequestParam(defaultValue = "all") String namespace,
                                        @RequestParam(required = false) String clusterId) {
        for (Map<String, Object> row : collectIssues(namespace, clusterId)) {
            if (id.equals(row.get("id"))) {
                Map<String, Object> out = base("operational-issue-evidence", namespace, clusterId);
                out.put("ok", true);
                out.put("issue", row);
                out.put("evidence", row.getOrDefault("evidence", Map.of()));
                return out;
            }
        }
        Map<String, Object> out = base("operational-issue-evidence", namespace, clusterId);
        out.put("ok", false);
        out.put("id", id);
        out.put("error", "ISSUE_NOT_FOUND_IN_CURRENT_LIVE_SNAPSHOT");
        out.put("message", "The issue id was not returned by the current live runtime snapshot.");
        return out;
    }

    @GetMapping("/{id}/actions")
    public Map<String, Object> actions(@PathVariable String id,
                                       @RequestParam(defaultValue = "all") String namespace,
                                       @RequestParam(required = false) String clusterId) {
        for (Map<String, Object> row : collectIssues(namespace, clusterId)) {
            if (id.equals(row.get("id"))) {
                Map<String, Object> out = base("operational-issue-actions", namespace, clusterId);
                out.put("ok", true);
                out.put("issue", row);
                out.put("items", row.getOrDefault("troubleshootingActions", List.of()));
                return out;
            }
        }
        Map<String, Object> out = base("operational-issue-actions", namespace, clusterId);
        out.put("ok", false);
        out.put("id", id);
        out.put("error", "ISSUE_NOT_FOUND_IN_CURRENT_LIVE_SNAPSHOT");
        out.put("message", "The issue id was not returned by the current live runtime snapshot.");
        out.put("items", List.of());
        return out;
    }

    private List<Map<String, Object>> collectIssues(String namespace, String clusterId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        collectDockerIssues(rows);
        collectKubernetesIssues(rows, namespace, clusterId);
        collectHelmIssues(rows, namespace, clusterId);
        collectExtensionIssues(rows);
        return dedupe(rows);
    }

    private void collectDockerIssues(List<Map<String, Object>> rows) {
        Map<String, Object> status = safeMap(docker.status());
        if (!Boolean.TRUE.equals(status.get("ok"))) {
            rows.add(issue("docker-source-unavailable", "ERROR", "Runtime", "Docker", "docker-engine",
                    "Docker Engine API is unavailable", str(status.getOrDefault("message", status.get("state"))),
                    "Verify Docker Desktop/Engine status, the mounted Docker socket and gateway permissions.", status));
            return;
        }

        for (Map<String, Object> c : items(docker.containers())) {
            String name = firstContainerName(c);
            String id = firstNonBlank(str(c.get("Id")), name);
            String state = str(c.get("State"));
            String statusText = str(c.get("Status"));
            if (statusText.toLowerCase(Locale.ROOT).contains("unhealthy")) {
                rows.add(issue("docker-container-unhealthy-" + safeId(id), "ERROR", "Runtime", "Docker container", name,
                        "Docker container is unhealthy", statusText,
                        "Open container health, logs and inspect from Docker Desktop before restarting the workload.", c));
            }
            if (Set.of("exited", "dead", "restarting", "created", "paused").contains(state.toLowerCase(Locale.ROOT))) {
                String sev = "restarting".equalsIgnoreCase(state) || "dead".equalsIgnoreCase(state) ? "ERROR" : "WARN";
                rows.add(issue("docker-container-state-" + safeId(id), sev, "Runtime", "Docker container", name,
                        "Docker container state is " + state, statusText,
                        "Review container logs, restart policy, dependency readiness and image availability.", c));
            }
        }

        for (Map<String, Object> risk : items(docker.projectRisks())) {
            rows.add(issue("docker-project-risk-" + safeId(risk.get("id")), normalizeSeverity(risk.get("severity")), "Runtime", "Docker project", str(risk.getOrDefault("name", risk.get("id"))),
                    "Docker project risk detected", str(risk.getOrDefault("message", risk.get("status"))),
                    "Open project failure analysis and verify startup order, logs and dependency health.", risk));
        }
        for (Map<String, Object> risk : items(docker.mountRiskAudit())) {
            rows.add(issue("docker-mount-risk-" + safeId(risk.get("id")), normalizeSeverity(risk.get("severity")), "Security", "Docker mount", str(risk.getOrDefault("container", risk.get("id"))),
                    "Risky Docker mount detected", str(risk.get("message")),
                    "Inspect mount configuration and remove unnecessary host or socket bindings.", risk));
        }
        for (Map<String, Object> risk : items(docker.containerSecurityAudit())) {
            if ("OK".equalsIgnoreCase(str(risk.get("severity")))) continue;
            rows.add(issue("docker-security-risk-" + safeId(risk.get("id")), normalizeSeverity(risk.get("severity")), "Security", "Docker container", str(risk.getOrDefault("name", risk.get("id"))),
                    "Docker container security posture issue", str(risk.get("message")),
                    "Review privileged mode, host namespaces, root user and root filesystem settings.", risk));
        }
        for (Map<String, Object> risk : items(docker.restartPolicyAudit())) {
            if ("OK".equalsIgnoreCase(str(risk.get("status")))) continue;
            rows.add(issue("docker-restart-policy-" + safeId(risk.get("id")), "WARN", "Reliability", "Docker container", str(risk.getOrDefault("name", risk.get("id"))),
                    "Docker restart policy or healthcheck coverage issue", "restartPolicy=" + str(risk.get("restartPolicy")) + ", healthcheck=" + str(risk.get("healthcheck")),
                    "Configure restart policy and healthcheck for operationally critical containers.", risk));
        }
        for (Map<String, Object> exposure : items(docker.networkExposure())) {
            rows.add(issue("docker-network-exposure-" + safeId(exposure.get("id")), "INFO", "Network", "Docker port", str(exposure.getOrDefault("container", exposure.get("id"))),
                    "Docker container publishes a host port", "Host port " + str(exposure.get("publicPort")) + " -> container port " + str(exposure.get("privatePort")),
                    "Verify the published port is intentional and protected by the expected local network boundary.", exposure));
        }
    }

    private void collectKubernetesIssues(List<Map<String, Object>> rows, String namespace, String clusterId) {
        Map<String, Object> pods = safeMap(kubernetes.resource("pods", namespace, clusterId));
        Map<String, Object> events = safeMap(kubernetes.events(namespace, clusterId));
        Map<String, Object> nodes = safeMap(kubernetes.nodes(clusterId));
        Map<String, Object> pvcs = safeMap(kubernetes.resource("persistentvolumeclaims", namespace, clusterId));
        Map<String, Object> services = safeMap(kubernetes.resource("services", namespace, clusterId));
        Map<String, Object> endpoints = safeMap(kubernetes.resource("endpoints", namespace, clusterId));
        Map<String, Object> ingress = safeMap(kubernetes.resource("ingress", namespace, clusterId));

        boolean live = Boolean.TRUE.equals(pods.get("live")) || Boolean.TRUE.equals(events.get("live")) || Boolean.TRUE.equals(nodes.get("live"));
        if (!live) {
            rows.add(issue("kubernetes-source-unavailable", "ERROR", "Runtime", "Kubernetes", "kubectl-current-context",
                    "Kubernetes runtime source is unavailable", firstNonBlank(str(pods.get("error")), str(nodes.get("error")), str(events.get("error")), "kubectl returned no live data"),
                    "Verify KUBECONFIG, cluster reachability and gateway kubectl permissions.", evidenceMap("pods", pods.get("toolStatus"), "nodes", nodes.get("toolStatus"), "events", events.get("toolStatus"))));
            return;
        }

        for (Map<String, Object> pod : items(pods)) detectPodIssues(rows, pod);
        for (Map<String, Object> event : items(events)) detectEventIssues(rows, event);
        for (Map<String, Object> node : items(nodes)) detectNodeIssues(rows, node);
        for (Map<String, Object> pvc : items(pvcs)) detectPvcIssues(rows, pvc);
        detectServiceEndpointIssues(rows, items(services), items(endpoints));
        detectIngressIssues(rows, items(ingress));
    }

    private void collectHelmIssues(List<Map<String, Object>> rows, String namespace, String clusterId) {
        Map<String, Object> releases = safeMap(kubernetes.helmReleases(namespace, clusterId));
        if (!Boolean.TRUE.equals(releases.get("live"))) return;
        for (Map<String, Object> release : items(releases)) {
            String status = firstNonBlank(str(release.get("status")), str(release.get("Status")));
            if (status.isBlank() || "deployed".equalsIgnoreCase(status)) continue;
            String name = firstNonBlank(str(release.get("name")), str(release.get("Name")), str(release.get("chart")));
            String ns = firstNonBlank(str(release.get("namespace")), str(release.get("Namespace")), "default");
            String sev = status.toLowerCase(Locale.ROOT).contains("failed") ? "ERROR" : "WARN";
            rows.add(issue("helm-release-" + safeId(ns + "-" + name), sev, "Release", "Helm release", ns + "/" + name,
                    "Helm release is not deployed", "status=" + status,
                    "Open Helm status, values and history before rollback or uninstall.", release));
        }
    }

    private void collectExtensionIssues(List<Map<String, Object>> rows) {
        ExtensionControlController controller = extensionControl.getIfAvailable();
        if (controller == null) return;
        Map<String, Object> summary;
        try {
            summary = safeMap(controller.extensionSummary());
        } catch (Exception e) {
            rows.add(issue("extensions-source-unavailable", "WARN", "Extensions", "Extension control", "extensions",
                    "Extension control-plane summary is unavailable", e.getMessage(),
                    "Open the Extensions panel and verify kubectl/Docker access if extensions are required.", evidenceMap("error", e.getMessage())));
            return;
        }
        for (Map<String, Object> ext : items(summary)) {
            String state = str(ext.get("state"));
            boolean disabledDefault = Boolean.FALSE.equals(ext.get("enabledByDefault")) || "DISABLED".equals(ext.get("defaultState"));
            if ("RUNNING".equalsIgnoreCase(state) || (disabledDefault && ("DISABLED_BY_DEFAULT".equalsIgnoreCase(state) || "STOPPED".equalsIgnoreCase(state)))) {
                continue;
            }
            String severity = "KUBERNETES_UNAVAILABLE".equalsIgnoreCase(state) ? "WARN" : "ERROR";
            rows.add(issue("extension-runtime-" + safeId(ext.get("id")), severity, "Extensions", "Extension", str(ext.getOrDefault("title", ext.get("id"))),
                    "Extension runtime is not healthy", "state=" + state,
                    "Open extension diagnostics and start, restart or repair the extension from the Extensions panel.", ext));
        }
    }

    private void detectPodIssues(List<Map<String, Object>> rows, Map<String, Object> pod) {
        Map meta = asMap(pod.get("metadata"));
        Map status = asMap(pod.get("status"));
        String ns = firstNonBlank(str(meta.get("namespace")), "default");
        String name = firstNonBlank(str(meta.get("name")), "pod");
        String phase = str(status.get("phase"));
        if (Set.of("Pending", "Failed", "Unknown").contains(phase)) {
            rows.add(issue("k8s-pod-phase-" + safeId(ns + "-" + name), severityForPodPhase(phase), "Runtime", "Pod", ns + "/" + name,
                    "Kubernetes pod phase is " + phase, phase,
                    "Open pod diagnostics, describe output and logs before delete/restart actions.", pod));
        }
        Object statuses = status.get("containerStatuses");
        if (statuses instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> cs)) continue;
                String cname = str(cs.get("name"));
                int restarts = intValue(cs.get("restartCount"));
                if (restarts >= 5) {
                    rows.add(issue("k8s-pod-restarts-" + safeId(ns + "-" + name + "-" + cname), "WARN", "Runtime", "Pod", ns + "/" + name,
                            "Kubernetes container restart count is high", cname + " restarted " + restarts + " times",
                            "Check recent logs, probes, resource limits and rollout history.", pod));
                }
                Map state = asMap(cs.get("state"));
                Map waiting = asMap(state.get("waiting"));
                Map terminated = asMap(state.get("terminated"));
                String reason = firstNonBlank(str(waiting.get("reason")), str(terminated.get("reason")));
                if (Set.of("CrashLoopBackOff", "ImagePullBackOff", "ErrImagePull", "CreateContainerConfigError", "RunContainerError", "OOMKilled").contains(reason)) {
                    rows.add(issue("k8s-container-state-" + safeId(ns + "-" + name + "-" + cname + "-" + reason), "ERROR", "Runtime", "Pod", ns + "/" + name,
                            "Kubernetes container state is " + reason, cname,
                            "Inspect image pull credentials, environment/config, probes, resource limits and recent container logs.", pod));
                }
            }
        }
    }

    private void detectEventIssues(List<Map<String, Object>> rows, Map<String, Object> event) {
        if (!"Warning".equalsIgnoreCase(str(event.get("type")))) return;
        Map meta = asMap(event.get("metadata"));
        Map involved = asMap(event.get("involvedObject"));
        String ns = firstNonBlank(str(involved.get("namespace")), str(meta.get("namespace")), "default");
        String kind = firstNonBlank(str(involved.get("kind")), "Event");
        String name = firstNonBlank(str(involved.get("name")), str(meta.get("name")), "kubernetes");
        String reason = firstNonBlank(str(event.get("reason")), "Warning");
        rows.add(issue("k8s-warning-event-" + safeId(ns + "-" + kind + "-" + name + "-" + reason), "WARN", "Runtime", kind, ns + "/" + name,
                "Kubernetes warning event", reason + ": " + str(event.get("message")),
                "Open the involved resource, check describe output and correlate with pod/service state.", event));
    }

    private void detectNodeIssues(List<Map<String, Object>> rows, Map<String, Object> node) {
        Map meta = asMap(node.get("metadata"));
        Map status = asMap(node.get("status"));
        String name = firstNonBlank(str(meta.get("name")), "node");
        Object conditions = status.get("conditions");
        if (!(conditions instanceof List<?> list)) return;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> c)) continue;
            String type = str(c.get("type"));
            String value = str(c.get("status"));
            boolean problem = ("Ready".equals(type) && !"True".equals(value)) || (!"Ready".equals(type) && "True".equals(value));
            if (problem) {
                rows.add(issue("k8s-node-condition-" + safeId(name + "-" + type), "ERROR", "Runtime", "Node", name,
                        "Kubernetes node condition is unhealthy", type + "=" + value + " " + str(c.get("message")),
                        "Check node pressure, kubelet status, Docker/container runtime and cluster events.", node));
            }
        }
    }

    private void detectPvcIssues(List<Map<String, Object>> rows, Map<String, Object> pvc) {
        Map meta = asMap(pvc.get("metadata"));
        Map status = asMap(pvc.get("status"));
        String phase = str(status.get("phase"));
        if (!phase.isBlank() && !"Bound".equals(phase)) {
            String ns = firstNonBlank(str(meta.get("namespace")), "default");
            String name = firstNonBlank(str(meta.get("name")), "pvc");
            rows.add(issue("k8s-pvc-phase-" + safeId(ns + "-" + name), "WARN", "Storage", "PersistentVolumeClaim", ns + "/" + name,
                    "Kubernetes PVC is not bound", "phase=" + phase,
                    "Verify storage class, provisioner, quotas and pending volume events.", pvc));
        }
    }

    private void detectServiceEndpointIssues(List<Map<String, Object>> rows, List<Map<String, Object>> services, List<Map<String, Object>> endpoints) {
        Map<String, Map<String, Object>> endpointsByKey = new HashMap<>();
        for (Map<String, Object> ep : endpoints) {
            Map meta = asMap(ep.get("metadata"));
            endpointsByKey.put(str(meta.get("namespace")) + "/" + str(meta.get("name")), ep);
        }
        for (Map<String, Object> svc : services) {
            Map meta = asMap(svc.get("metadata"));
            Map spec = asMap(svc.get("spec"));
            if ("ExternalName".equals(str(spec.get("type")))) continue;
            String ns = firstNonBlank(str(meta.get("namespace")), "default");
            String name = firstNonBlank(str(meta.get("name")), "service");
            Map<String, Object> ep = endpointsByKey.get(ns + "/" + name);
            Object subsets = ep == null ? null : ep.get("subsets");
            if (!(subsets instanceof List<?> subsetRows) || subsetRows.isEmpty()) {
                rows.add(issue("k8s-service-no-endpoints-" + safeId(ns + "-" + name), "WARN", "Network", "Service", ns + "/" + name,
                        "Kubernetes service has no ready endpoint subsets", str(spec.get("type")),
                        "Verify selector labels, pod readiness, endpoint slices and namespace network policy.", Map.of("service", svc, "endpoints", ep == null ? Map.of() : ep)));
            }
        }
    }

    private void detectIngressIssues(List<Map<String, Object>> rows, List<Map<String, Object>> ingresses) {
        for (Map<String, Object> ingress : ingresses) {
            Map meta = asMap(ingress.get("metadata"));
            Map spec = asMap(ingress.get("spec"));
            String ns = firstNonBlank(str(meta.get("namespace")), "default");
            String name = firstNonBlank(str(meta.get("name")), "ingress");
            Object tls = spec.get("tls");
            if (!(tls instanceof List<?> tlsRows) || tlsRows.isEmpty()) {
                rows.add(issue("k8s-ingress-no-tls-" + safeId(ns + "-" + name), "WARN", "Network", "Ingress", ns + "/" + name,
                        "Kubernetes ingress has no TLS section", "TLS not configured",
                        "Verify whether the ingress is intentionally HTTP-only or add a TLS secret.", ingress));
            }
        }
    }

    private Map<String, Object> sourceHealth(String namespace, String clusterId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("docker", sourceState("Docker Engine", docker.status()));
        out.put("kubernetes", sourceState("Kubernetes", kubernetes.resource("pods", namespace, clusterId)));
        out.put("helm", sourceState("Helm", kubernetes.helmReleases(namespace, clusterId)));
        ExtensionControlController controller = extensionControl.getIfAvailable();
        if (controller == null) {
            out.put("extensions", Map.of("source", "Extensions", "state", "UNAVAILABLE", "live", false));
        } else {
            try {
                out.put("extensions", sourceState("Extensions", controller.extensionSummary()));
            } catch (Exception e) {
                out.put("extensions", evidenceMap("source", "Extensions", "state", "UNAVAILABLE", "live", false, "message", e.getMessage()));
            }
        }
        return out;
    }

    private Map<String, Object> sourceState(String label, Map<String, Object> payload) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean live = Boolean.TRUE.equals(payload.get("live")) || Boolean.TRUE.equals(payload.get("ok"));
        out.put("source", label);
        out.put("live", live);
        out.put("state", live ? "LIVE" : "UNAVAILABLE");
        out.put("tool", payload.getOrDefault("tool", payload.getOrDefault("mode", label)));
        out.put("resource", payload.getOrDefault("resource", "status"));
        out.put("message", firstNonBlank(str(payload.get("message")), str(payload.get("error")), str(payload.get("state"))));
        out.put("toolStatus", payload.get("toolStatus"));
        return out;
    }

    private List<Map<String, Object>> filter(List<Map<String, Object>> rows, String severity, String source) {
        String sev = severity == null ? "" : severity.trim().toUpperCase(Locale.ROOT);
        String src = source == null ? "" : source.trim().toLowerCase(Locale.ROOT);
        if (sev.isBlank() && src.isBlank()) return rows;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (!sev.isBlank() && !sev.equals(str(row.get("severity")).toUpperCase(Locale.ROOT))) continue;
            if (!src.isBlank() && !str(row.get("sourceType")).toLowerCase(Locale.ROOT).contains(src)) continue;
            out.add(row);
        }
        return out;
    }

    private Map<String, Object> issue(String id, String severity, String category, String resourceType, String name,
                                      String title, String message, String action, Object evidence) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", safeId(id));
        row.put("name", name);
        row.put("title", title);
        row.put("severity", normalizeSeverity(severity));
        row.put("status", normalizeSeverity(severity));
        row.put("category", category);
        row.put("kind", resourceType);
        row.put("resourceType", resourceType);
        row.put("source", sourceFor(resourceType));
        row.put("sourceType", sourceFor(resourceType));
        row.put("message", message == null || message.isBlank() ? title : message);
        row.put("recommendedAction", action);
        String issueId = safeId(id);
        row.put("evidenceEndpoint", "/api/platform/issues/" + issueId + "/evidence");
        row.put("actionsEndpoint", "/api/platform/issues/" + issueId + "/actions");
        row.put("detectedAt", Instant.now().toString());
        row.put("realDataOnly", true);
        row.put("evidence", evidence);
        List<Map<String, Object>> actions = troubleshootingActions(issueId, resourceType, name, evidence);
        row.put("troubleshootingActions", actions);
        row.put("actionCount", actions.size());
        return row;
    }


    private List<Map<String, Object>> troubleshootingActions(String issueId, String resourceType, String name, Object evidence) {
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(action("evidence", "Evidence", "GET", "/api/platform/issues/" + issueId + "/evidence", "Open the exact live evidence attached to this issue.", false));
        Map ev = asMap(evidence);
        String type = str(resourceType).toLowerCase(Locale.ROOT);

        if (type.contains("docker project")) {
            String project = dockerProjectName(name, ev);
            if (!project.isBlank()) {
                actions.add(action("docker-project-failure-analysis", "Failure analysis", "GET", "/api/runtime/docker/projects/" + safePath(project) + "/failure-analysis", "Analyze failing project containers, logs and runtime state.", false));
                actions.add(action("docker-project-logs", "Logs", "GET", "/api/runtime/docker/projects/" + safePath(project) + "/logs", "Open logs for all containers in this Docker project.", false));
                actions.add(action("docker-project-dependency-map", "Dependency map", "GET", "/api/runtime/docker/projects/" + safePath(project) + "/dependency-map", "Show container, image, volume and network dependencies for this project.", false));
                actions.add(action("docker-project-startup-order", "Startup order", "GET", "/api/runtime/docker/projects/" + safePath(project) + "/startup-order", "Inspect project startup ordering derived from Docker labels and container state.", false));
                actions.add(action("docker-project-health-report", "Health report", "GET", "/api/runtime/docker/projects/" + safePath(project) + "/health-report", "Open project health findings without executing any mutation.", false));
                actions.add(plannedAction("plan-docker-project-restart", "Plan restart", "docker", "project.restart", project, Map.of(), "Generate a Safe Action Plan before restarting all containers in this Docker project.", "MEDIUM"));
                actions.add(plannedAction("plan-docker-project-stop", "Plan stop", "docker", "project.stop", project, Map.of(), "Generate a Safe Action Plan before stopping all containers in this Docker project.", "HIGH"));
            }
            return actions;
        }

        if (type.contains("docker")) {
            String containerId = dockerContainerId(ev, name);
            if (!containerId.isBlank()) {
                actions.add(action("docker-container-details", "Details", "GET", "/api/runtime/docker/containers/" + safePath(containerId) + "/details", "Open inspect, stats, process list, filesystem changes and recent logs.", false));
                actions.add(action("docker-container-logs", "Logs", "GET", "/api/runtime/docker/containers/" + safePath(containerId) + "/logs?tail=180&timestamps=true", "Open recent stdout/stderr logs from Docker Engine.", false));
                actions.add(action("docker-container-log-analysis", "Log analysis", "GET", "/api/runtime/docker/containers/" + safePath(containerId) + "/log-analysis?tail=240", "Scan live logs for error, timeout, permission and connection failure patterns.", false));
                actions.add(action("docker-container-health", "Health", "GET", "/api/runtime/docker/containers/" + safePath(containerId) + "/health", "Read Docker healthcheck and container state from inspect.", false));
                actions.add(action("docker-container-inspect", "Inspect", "GET", "/api/runtime/docker/containers/" + safePath(containerId) + "/inspect", "Open the raw Docker inspect payload for this container.", false));
                actions.add(plannedAction("plan-docker-container-restart", "Plan restart", "docker", "container.restart", containerId, Map.of(), "Generate a Safe Action Plan before restarting this container.", "MEDIUM"));
                actions.add(plannedAction("plan-docker-container-kill", "Plan kill", "docker", "container.kill", containerId, Map.of("signal", "SIGKILL"), "Generate a Safe Action Plan before killing this container.", "HIGH"));
                actions.add(plannedAction("plan-docker-container-remove", "Plan remove", "docker", "container.remove", containerId, Map.of("force", false, "volumes", false), "Generate a Safe Action Plan before removing this container.", "HIGH"));
            }
            return actions;
        }

        if (type.contains("deployment") || type.contains("statefulset") || type.contains("daemonset") || type.contains("job") || type.contains("cronjob")) {
            String ns = kubernetesNamespace(name, ev);
            String workload = kubernetesName(name, ev);
            String kind = workloadKind(resourceType);
            if (!workload.isBlank()) {
                actions.add(action("k8s-workload-root-cause", "Root cause", "GET", "/api/kubernetes/workloads/" + kind + "/" + safePath(ns) + "/" + safePath(workload) + "/root-cause", "Run workload root-cause analysis from live kubectl data.", false));
                actions.add(action("k8s-workload-detail", "Detail", "GET", "/api/kubernetes/workloads/" + kind + "/" + safePath(ns) + "/" + safePath(workload) + "/detail", "Open workload detail assembled from live resources.", false));
                actions.add(action("k8s-workload-dependency-map", "Dependency map", "GET", "/api/kubernetes/workloads/" + kind + "/" + safePath(ns) + "/" + safePath(workload) + "/dependency-map", "Open workload service, pod, event and owner dependencies.", false));
                if (!"job".equals(kind) && !"cronjob".equals(kind)) {
                    actions.add(action("k8s-workload-rollout-status", "Rollout", "GET", "/api/kubernetes/workloads/" + kind + "/" + safePath(ns) + "/" + safePath(workload) + "/rollout-status", "Open rollout status for this workload.", false));
                    actions.add(plannedAction("plan-k8s-workload-restart", "Plan rollout restart", "kubernetes", "workload.restart", ns + "/" + workload, Map.of("kind", kind, "namespace", ns, "name", workload), "Generate a Safe Action Plan before restarting this workload rollout.", "MEDIUM"));
                    actions.add(plannedAction("plan-k8s-workload-scale-zero", "Plan scale", "kubernetes", "workload.scale", ns + "/" + workload, Map.of("kind", kind, "namespace", ns, "name", workload, "replicas", 0), "Generate a Safe Action Plan before scaling this workload. Edit replicas in the execute payload if needed.", "HIGH"));
                }
            }
            return actions;
        }

        if (type.contains("pod")) {
            String ns = kubernetesNamespace(name, ev);
            String pod = kubernetesName(name, ev);
            if (!pod.isBlank()) {
                actions.add(action("k8s-pod-root-cause", "Root cause", "GET", "/api/kubernetes/pods/" + safePath(ns) + "/" + safePath(pod) + "/root-cause", "Run pod root-cause analysis from describe, events, logs and status.", false));
                actions.add(action("k8s-pod-diagnostics", "Diagnostics", "GET", "/api/kubernetes/pods/" + safePath(ns) + "/" + safePath(pod) + "/diagnostics?tail=180", "Open pod diagnostics assembled from live kubectl calls.", false));
                actions.add(action("k8s-pod-logs", "Logs", "GET", "/api/kubernetes/pods/" + safePath(ns) + "/" + safePath(pod) + "/logs", "Open recent pod logs.", false));
                actions.add(action("k8s-pod-describe", "Describe", "GET", "/api/kubernetes/pods/" + safePath(ns) + "/" + safePath(pod) + "/describe", "Open kubectl describe output for the pod.", false));
                actions.add(plannedAction("plan-k8s-pod-restart", "Plan restart", "kubernetes", "pod.restart", ns + "/" + pod, Map.of("kind", "pod", "namespace", ns, "name", pod), "Generate a Safe Action Plan before restarting/deleting this pod.", "MEDIUM"));
                actions.add(plannedAction("plan-k8s-pod-delete", "Plan delete", "kubernetes", "pod.delete", ns + "/" + pod, Map.of("kind", "pod", "namespace", ns, "name", pod), "Generate a Safe Action Plan before deleting this pod.", "HIGH"));
            }
            return actions;
        }

        if (type.contains("service")) {
            String ns = kubernetesNamespace(name, ev);
            String svc = kubernetesName(name, asMap(ev.getOrDefault("service", ev)));
            if (svc.isBlank()) svc = kubernetesName(name, ev);
            if (!svc.isBlank()) {
                actions.add(action("k8s-service-connectivity", "Connectivity", "GET", "/api/kubernetes/services/" + safePath(ns) + "/" + safePath(svc) + "/connectivity", "Check service selectors, endpoints and connectivity indicators.", false));
                actions.add(action("k8s-service-yaml", "YAML", "GET", "/api/kubernetes/services/" + safePath(ns) + "/" + safePath(svc) + "/yaml", "Open the live service YAML.", false));
                actions.add(action("k8s-service-describe", "Describe", "GET", "/api/kubernetes/services/" + safePath(ns) + "/" + safePath(svc) + "/describe", "Open kubectl describe output for the service.", false));
            }
            return actions;
        }

        if (type.contains("ingress")) {
            String ns = kubernetesNamespace(name, ev);
            String ingress = kubernetesName(name, ev);
            if (!ingress.isBlank()) {
                actions.add(action("k8s-ingress-connectivity", "Connectivity", "GET", "/api/kubernetes/ingress/" + safePath(ns) + "/" + safePath(ingress) + "/connectivity", "Check ingress rules, TLS and backend wiring.", false));
                actions.add(action("k8s-ingress-yaml", "YAML", "GET", "/api/kubernetes/ingresses/" + safePath(ns) + "/" + safePath(ingress) + "/yaml", "Open the live ingress YAML.", false));
                actions.add(action("k8s-ingress-describe", "Describe", "GET", "/api/kubernetes/ingresses/" + safePath(ns) + "/" + safePath(ingress) + "/describe", "Open kubectl describe output for the ingress.", false));
            }
            return actions;
        }

        if (type.contains("node")) {
            String node = kubernetesName(name, ev);
            if (!node.isBlank()) {
                actions.add(action("k8s-node-describe", "Describe node", "GET", "/api/kubernetes/nodes/" + safePath(node) + "/describe", "Open kubectl describe output for the node.", false));
            }
            return actions;
        }

        if (type.contains("persistentvolumeclaim")) {
            String ns = kubernetesNamespace(name, ev);
            String pvc = kubernetesName(name, ev);
            if (!pvc.isBlank()) {
                actions.add(action("k8s-pvc-detail", "PVC details", "GET", "/api/kubernetes/problems?namespace=" + safePath(ns), "Open live Kubernetes problem detector output for this namespace.", false));
            }
            return actions;
        }

        if (type.contains("namespace")) {
            String ns = kubernetesName(name, ev);
            if (!ns.isBlank()) {
                actions.add(action("k8s-namespace-dependency-map", "Dependency map", "GET", "/api/kubernetes/namespaces/" + safePath(ns) + "/dependency-map", "Open namespace-level dependency map from live resources.", false));
                actions.add(action("k8s-namespace-describe", "Describe", "GET", "/api/kubernetes/namespaces/" + safePath(ns) + "/describe", "Open kubectl describe output for the namespace.", false));
                actions.add(action("k8s-namespace-quotas", "Quotas", "GET", "/api/kubernetes/namespaces/" + safePath(ns) + "/quotas", "Open namespace quota and limit range information.", false));
            }
            return actions;
        }

        if (type.contains("helm")) {
            String ns = kubernetesNamespace(name, ev);
            String release = kubernetesName(name, ev);
            if (!release.isBlank()) {
                actions.add(action("helm-status", "Status", "GET", "/api/kubernetes/helm/releases/" + safePath(ns) + "/" + safePath(release) + "/status", "Open helm status for this release.", false));
                actions.add(action("helm-values", "Values", "GET", "/api/kubernetes/helm/releases/" + safePath(ns) + "/" + safePath(release) + "/values", "Open helm values for this release.", false));
                actions.add(action("helm-history", "History", "GET", "/api/kubernetes/helm/releases/" + safePath(ns) + "/" + safePath(release) + "/history", "Open helm revision history.", false));
                actions.add(plannedAction("plan-helm-rollback", "Plan rollback", "helm", "helm.rollback", ns + "/" + release, Map.of("namespace", ns, "name", release, "revision", 0), "Generate a Safe Action Plan before rolling back this Helm release.", "MEDIUM"));
                actions.add(plannedAction("plan-helm-uninstall", "Plan uninstall", "helm", "helm.uninstall", ns + "/" + release, Map.of("namespace", ns, "name", release), "Generate a Safe Action Plan before uninstalling this Helm release.", "HIGH"));
            }
            return actions;
        }

        if (type.contains("extension")) {
            String extensionId = firstNonBlank(str(ev.get("id")), str(ev.get("slug")), safeId(name));
            actions.add(action("extension-diagnostics", "Diagnostics", "GET", "/api/extensions/" + safePath(extensionId) + "/diagnostics", "Open extension control-plane diagnostics.", false));
            actions.add(action("extension-status", "Status", "GET", "/api/extensions/" + safePath(extensionId) + "/status", "Open current extension runtime status.", false));
            return actions;
        }

        return actions;
    }

    private Map<String, Object> action(String id, String label, String method, String endpoint, String description, boolean dangerous) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("label", label);
        out.put("method", method);
        out.put("endpoint", endpoint);
        out.put("description", description);
        out.put("dangerous", dangerous);
        out.put("liveOnly", true);
        return out;
    }

    private Map<String, Object> plannedAction(String id, String label, String domain, String action, String target,
                                              Map<String, Object> parameters, String description, String risk) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("label", label);
        out.put("method", "POST");
        out.put("endpoint", "docker".equals(domain) ? "/api/runtime/docker/actions/plan" : "/api/kubernetes/actions/plan");
        out.put("description", description);
        out.put("dangerous", true);
        out.put("requiresPlan", true);
        out.put("risk", risk);
        out.put("executeEndpoint", "/api/platform/actions/execute");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("domain", domain);
        body.put("action", action);
        body.put("target", target);
        Map<String, Object> safeParameters = parameters == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parameters);
        body.put("parameters", safeParameters);
        if (!"docker".equals(domain) && target.contains("/")) {
            body.putIfAbsent("namespace", target.substring(0, target.indexOf('/')));
            body.putIfAbsent("name", target.substring(target.indexOf('/') + 1));
        }
        if (!"docker".equals(domain)) {
            if (safeParameters.containsKey("kind")) body.putIfAbsent("kind", safeParameters.get("kind"));
            if (safeParameters.containsKey("namespace")) body.putIfAbsent("namespace", safeParameters.get("namespace"));
            if (safeParameters.containsKey("name")) body.putIfAbsent("name", safeParameters.get("name"));
        }
        out.put("body", body);
        out.put("liveOnly", true);
        return out;
    }

    private String dockerContainerId(Map ev, String name) {
        String id = firstNonBlank(str(ev.get("Id")), str(ev.get("id")), str(ev.get("containerId")));
        if (id.contains(":")) id = id.substring(0, id.indexOf(':'));
        if (id.startsWith("project:")) return "";
        if (!id.isBlank()) return id;
        return firstNonBlank(str(ev.get("container")), str(ev.get("name")), name);
    }

    private String dockerProjectName(String name, Map ev) {
        String id = str(ev.get("id"));
        if (id.startsWith("project:")) return id.substring("project:".length());
        return firstNonBlank(str(ev.get("project")), str(ev.get("name")), name);
    }

    private String workloadKind(String resourceType) {
        String type = str(resourceType).toLowerCase(Locale.ROOT);
        if (type.contains("statefulset")) return "statefulset";
        if (type.contains("daemonset")) return "daemonset";
        if (type.contains("cronjob")) return "cronjob";
        if (type.contains("job")) return "job";
        return "deployment";
    }

    private String kubernetesNamespace(String displayName, Map ev) {
        Map meta = asMap(ev.get("metadata"));
        Map involved = asMap(ev.get("involvedObject"));
        Map service = asMap(ev.get("service"));
        Map serviceMeta = asMap(service.get("metadata"));
        String display = str(displayName);
        String fromDisplay = display.contains("/") ? display.substring(0, display.indexOf('/')) : "";
        return firstNonBlank(str(meta.get("namespace")), str(involved.get("namespace")), str(serviceMeta.get("namespace")), fromDisplay, "default");
    }

    private String kubernetesName(String displayName, Map ev) {
        Map meta = asMap(ev.get("metadata"));
        Map involved = asMap(ev.get("involvedObject"));
        String display = str(displayName);
        String fromDisplay = display.contains("/") ? display.substring(display.indexOf('/') + 1) : display;
        return firstNonBlank(str(meta.get("name")), str(involved.get("name")), str(ev.get("name")), str(ev.get("Name")), fromDisplay);
    }

    private String safePath(String value) {
        return safeId(value);
    }

    private String sourceFor(String resourceType) {
        String type = resourceType == null ? "" : resourceType.toLowerCase(Locale.ROOT);
        if (type.contains("docker")) return "docker-engine-api";
        if (type.contains("helm")) return "helm";
        if (type.contains("extension")) return "extension-control-plane";
        if (type.contains("pod") || type.contains("node") || type.contains("service") || type.contains("ingress") || type.contains("persistentvolume") || type.contains("deployment") || type.contains("statefulset") || type.contains("daemonset") || type.contains("job") || type.contains("namespace")) return "kubectl";
        return "runtime";
    }

    private Map<String, Object> summary(List<Map<String, Object>> rows) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", rows.size());
        out.put("errors", rows.stream().filter(r -> "ERROR".equals(r.get("severity")) || "CRITICAL".equals(r.get("severity"))).count());
        out.put("warnings", rows.stream().filter(r -> "WARN".equals(r.get("severity"))).count());
        out.put("info", rows.stream().filter(r -> "INFO".equals(r.get("severity"))).count());
        out.put("docker", rows.stream().filter(r -> str(r.get("sourceType")).contains("docker")).count());
        out.put("kubernetes", rows.stream().filter(r -> str(r.get("sourceType")).contains("kubectl")).count());
        out.put("helm", rows.stream().filter(r -> str(r.get("sourceType")).contains("helm")).count());
        out.put("extensions", rows.stream().filter(r -> str(r.get("sourceType")).contains("extension")).count());
        return out;
    }

    private List<Map<String, Object>> summaryItems(List<Map<String, Object>> rows) {
        Map<String, Object> s = summary(rows);
        List<Map<String, Object>> items = new ArrayList<>();
        for (String key : List.of("total", "errors", "warnings", "info", "docker", "kubernetes", "helm", "extensions")) {
            items.add(Map.of("id", key, "name", key, "status", key.equals("errors") && longValue(s.get(key)) > 0 ? "ERROR" : key.equals("warnings") && longValue(s.get(key)) > 0 ? "WARN" : "OK", "count", s.get(key), "source", "operational-issues-summary"));
        }
        return items;
    }

    private Map<String, Object> base(String resource, String namespace, String clusterId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", true);
        out.put("realDataOnly", true);
        out.put("tool", "nebulaops-gateway");
        out.put("resource", resource);
        out.put("namespace", namespace);
        out.put("clusterId", clusterId == null || clusterId.isBlank() ? "current-context" : clusterId);
        out.put("generatedAt", Instant.now().toString());
        return out;
    }

    private List<Map<String, Object>> dedupe(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> seen = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) seen.putIfAbsent(str(row.get("id")), row);
        return new ArrayList<>(seen.values());
    }

    private List<Map<String, Object>> items(Map<String, Object> payload) {
        if (payload == null) return List.of();
        Object items = payload.get("items");
        if (items instanceof List<?> rows) return mapRows(rows);
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> m) {
            Object nested = m.get("items");
            if (nested instanceof List<?> rows) return mapRows(rows);
        }
        return List.of();
    }

    private List<Map<String, Object>> mapRows(List<?> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object row : rows) if (row instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        return out;
    }

    private Map<String, Object> safeMap(Map<String, Object> payload) {
        return payload == null ? new LinkedHashMap<>() : payload;
    }

    private Map<String, Object> evidenceMap(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out.put(str(pairs[i]), pairs[i + 1] == null ? "" : pairs[i + 1]);
        }
        return out;
    }

    private Map asMap(Object o) { return o instanceof Map ? (Map) o : Map.of(); }

    private String normalizeSeverity(Object severity) {
        String s = str(severity).toUpperCase(Locale.ROOT);
        if (s.equals("CRITICAL")) return "CRITICAL";
        if (s.equals("ERROR") || s.equals("FAILED") || s.equals("FAIL")) return "ERROR";
        if (s.equals("WARN") || s.equals("WARNING") || s.equals("DEGRADED")) return "WARN";
        return "INFO";
    }

    private int severityWeight(String severity) {
        return switch (normalizeSeverity(severity)) {
            case "CRITICAL" -> 0;
            case "ERROR" -> 1;
            case "WARN" -> 2;
            default -> 3;
        };
    }

    private String severityForPodPhase(String phase) {
        return "Failed".equals(phase) || "Unknown".equals(phase) ? "ERROR" : "WARN";
    }

    private String safeId(Object value) {
        String text = str(value);
        if (text.isBlank()) text = "runtime-issue";
        text = text.replaceAll("[^A-Za-z0-9_.:-]", "-").replaceAll("-+", "-");
        return text.length() > 180 ? text.substring(0, 180) : text;
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

    private int intValue(Object value) {
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(str(value)); } catch (Exception ignored) { return 0; }
    }

    private long longValue(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(str(value)); } catch (Exception ignored) { return 0L; }
    }

    private String str(Object value) { return value == null ? "" : String.valueOf(value); }
}
