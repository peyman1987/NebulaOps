package dev.nebulaops.progressive.service;

import dev.nebulaops.progressive.client.JsonAdapter;
import dev.nebulaops.progressive.client.ProcessExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class ProgressiveDeliveryService {
    private final ProcessExecutor exec;
    private final JsonAdapter json;
    private final RestTemplate rest = new RestTemplate();
    private final String auditUrl;
    private final String notificationUrl;
    private final String argocdUrl;
    private final String argocdToken;

    public ProgressiveDeliveryService(
            ProcessExecutor exec,
            JsonAdapter json,
            @Value("${nebulaops.audit.url:http://audit-service:8101}") String auditUrl,
            @Value("${nebulaops.notification.url:http://notification-service:8083}") String notificationUrl,
            @Value("${nebulaops.argocd.url:}") String argocdUrl,
            @Value("${nebulaops.argocd.token:}") String argocdToken) {
        this.exec = exec;
        this.json = json;
        this.auditUrl = auditUrl;
        this.notificationUrl = notificationUrl;
        this.argocdUrl = argocdUrl;
        this.argocdToken = argocdToken;
    }

    public Map<String, Object> overview(String namespace) {
        Map<String, Object> rollouts = rollouts(namespace);
        Map<String, Object> apps = applications();
        Map<String, Object> analyses = analysisRuns(namespace);
        Map<String, Object> experiments = experiments(namespace);
        int rolloutCount = sizeOfItems(rollouts);
        int appCount = sizeOfItems(apps);
        int analysisCount = sizeOfItems(analyses);
        int experimentCount = sizeOfItems(experiments);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("live", Boolean.TRUE.equals(rollouts.get("live")) || Boolean.TRUE.equals(apps.get("live")) || Boolean.TRUE.equals(analyses.get("live")) || Boolean.TRUE.equals(experiments.get("live")));
        response.put("realDataOnly", true);
        response.put("namespace", namespace);
        response.put("rolloutCount", rolloutCount);
        response.put("applicationCount", appCount);
        response.put("analysisRunCount", analysisCount);
        response.put("experimentCount", experimentCount);
        response.put("items", List.of(
                Map.of("name", "rollouts", "count", rolloutCount, "source", rollouts.getOrDefault("source", "kubectl"), "live", rollouts.get("live")),
                Map.of("name", "applications", "count", appCount, "source", apps.getOrDefault("source", "argocd"), "live", apps.get("live")),
                Map.of("name", "analysisRuns", "count", analysisCount, "source", analyses.getOrDefault("source", "kubectl"), "live", analyses.get("live")),
                Map.of("name", "experiments", "count", experimentCount, "source", experiments.getOrDefault("source", "kubectl"), "live", experiments.get("live"))
        ));
        response.put("rollouts", rollouts);
        response.put("applications", apps);
        response.put("analysisRuns", analyses);
        response.put("experiments", experiments);
        response.put("toolStatus", "Progressive Delivery Center reads only Argo Rollouts, Argo CD and Kubernetes runtime sources.");
        return response;
    }

    public Map<String, Object> rollouts(String namespace) {
        String ns = normalizeNamespace(namespace);
        String command = ns.equals("all")
                ? "kubectl get rollouts.argoproj.io -A -o json"
                : "kubectl get rollouts.argoproj.io -n " + safe(ns) + " -o json";
        return kubectlItems("rollouts", command, "kubectl rollouts.argoproj.io");
    }

    public Map<String, Object> rollout(String namespace, String name) {
        String ns = normalizeNamespace(namespace);
        String command = "kubectl get rollout " + safe(name) + " -n " + safe(ns) + " -o json";
        return kubectlObject("rollout", command, "kubectl rollout");
    }

    public Map<String, Object> analysisRuns(String namespace) {
        String ns = normalizeNamespace(namespace);
        String command = ns.equals("all")
                ? "kubectl get analysisruns.argoproj.io -A -o json"
                : "kubectl get analysisruns.argoproj.io -n " + safe(ns) + " -o json";
        return kubectlItems("analysisRuns", command, "kubectl analysisruns.argoproj.io");
    }

    public Map<String, Object> experiments(String namespace) {
        String ns = normalizeNamespace(namespace);
        String command = ns.equals("all")
                ? "kubectl get experiments.argoproj.io -A -o json"
                : "kubectl get experiments.argoproj.io -n " + safe(ns) + " -o json";
        return kubectlItems("experiments", command, "kubectl experiments.argoproj.io");
    }

    public Map<String, Object> applications() {
        Map<String, Object> cli = argocd("applications", "argocd app list -o json", 45);
        if (Boolean.TRUE.equals(cli.get("live"))) {
            return cli;
        }
        if (argocdUrl == null || argocdUrl.isBlank() || argocdToken == null || argocdToken.isBlank()) {
            return cli;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(argocdToken);
            ResponseEntity<Object> response = rest.exchange(argocdUrl.replaceAll("/$", "") + "/api/v1/applications", HttpMethod.GET, new HttpEntity<>(headers), Object.class);
            Object body = response.getBody();
            List<Object> items = extractItems(body);
            return Map.of("live", response.getStatusCode().is2xxSuccessful(), "realDataOnly", true, "source", "argocd-api", "items", items, "data", body, "toolStatus", "Argo CD applications loaded through the configured API token");
        } catch (Exception e) {
            return Map.of("live", false, "realDataOnly", true, "source", "argocd", "items", List.of(), "error", e.getClass().getSimpleName() + ": " + e.getMessage(), "toolStatus", cli.getOrDefault("toolStatus", Map.of()));
        }
    }

    public Map<String, Object> syncApplication(String app) {
        Map<String, Object> result = argocd("sync", "argocd app sync " + safe(app) + " -o json", 120);
        publish("PROGRESSIVE_ARGOCD_SYNC_REQUESTED", Boolean.TRUE.equals(result.get("live")) ? "INFO" : "WARN", Map.of("application", app, "result", result));
        return result;
    }

    public Map<String, Object> promote(String namespace, String name, boolean full) {
        String ns = normalizeNamespace(namespace);
        String command = "kubectl argo rollouts promote " + safe(name) + " -n " + safe(ns) + (full ? " --full" : "") + "";
        Map<String, Object> result = kubectlAction("promote", command);
        publish("PROGRESSIVE_ROLLOUT_PROMOTED", Boolean.TRUE.equals(result.get("live")) ? "INFO" : "WARN", Map.of("namespace", ns, "rollout", name, "full", full, "result", result));
        return result;
    }

    public Map<String, Object> abort(String namespace, String name) {
        String ns = normalizeNamespace(namespace);
        String command = "kubectl argo rollouts abort " + safe(name) + " -n " + safe(ns) + "";
        Map<String, Object> result = kubectlAction("abort", command);
        publish("PROGRESSIVE_ROLLOUT_ABORTED", Boolean.TRUE.equals(result.get("live")) ? "INFO" : "WARN", Map.of("namespace", ns, "rollout", name, "result", result));
        return result;
    }

    public Map<String, Object> restart(String namespace, String name) {
        String ns = normalizeNamespace(namespace);
        String command = "kubectl argo rollouts restart " + safe(name) + " -n " + safe(ns) + "";
        Map<String, Object> result = kubectlAction("restart", command);
        publish("PROGRESSIVE_ROLLOUT_RESTARTED", Boolean.TRUE.equals(result.get("live")) ? "INFO" : "WARN", Map.of("namespace", ns, "rollout", name, "result", result));
        return result;
    }

    public Map<String, Object> history(String namespace, String name) {
        String ns = normalizeNamespace(namespace);
        return kubectlAction("history", "kubectl argo rollouts history " + safe(name) + " -n " + safe(ns) + "");
    }

    private Map<String, Object> kubectlItems(String label, String command, String source) {
        ProcessExecutor.Result r = exec.shell(command, 45);
        Object data = parseOrRaw(r.stdout());
        List<Object> items = r.ok() ? extractItems(data) : List.of();
        return Map.of("live", r.ok(), "realDataOnly", true, "kind", label, "source", source, "items", items, "data", data, "toolStatus", r.status());
    }

    private Map<String, Object> kubectlObject(String label, String command, String source) {
        ProcessExecutor.Result r = exec.shell(command, 45);
        Object data = parseOrRaw(r.stdout());
        return Map.of("live", r.ok(), "realDataOnly", true, "kind", label, "source", source, "data", data, "items", r.ok() ? List.of(data) : List.of(), "toolStatus", r.status());
    }

    private Map<String, Object> kubectlAction(String action, String command) {
        ProcessExecutor.Result r = exec.shell(command, 120);
        Object data = parseOrRaw(r.stdout());
        return Map.of("live", r.ok(), "realDataOnly", true, "action", action, "source", "kubectl-argo-rollouts", "data", data, "items", r.ok() ? extractItems(data) : List.of(), "toolStatus", r.status());
    }

    private Map<String, Object> argocd(String label, String command, int timeout) {
        ProcessExecutor.Result r = exec.shell(command, timeout);
        Object data = parseOrRaw(r.stdout());
        List<Object> items = r.ok() ? extractItems(data) : List.of();
        return Map.of("live", r.ok(), "realDataOnly", true, "kind", label, "source", "argocd-cli", "items", items, "data", data, "toolStatus", r.status());
    }

    private Object parseOrRaw(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return Map.of();
        }
        try {
            return json.parse(stdout);
        } catch (Exception e) {
            return Map.of("raw", stdout, "parseError", e.getMessage());
        }
    }

    private List<Object> extractItems(Object data) {
        if (data instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (data instanceof Map<?,?> map) {
            Object items = map.get("items");
            if (items instanceof List<?> list) {
                return new ArrayList<>(list);
            }
            Object result = map.get("result");
            if (result instanceof List<?> list) {
                return new ArrayList<>(list);
            }
        }
        return List.of();
    }

    private int sizeOfItems(Map<String, Object> payload) {
        Object items = payload.get("items");
        return items instanceof List<?> list ? list.size() : 0;
    }

    private String normalizeNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return "all";
        }
        return namespace.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9_.:/@-]", "");
    }

    private void publish(String type, String severity, Map<String, Object> payload) {
        String correlationId = "pd-" + UUID.randomUUID();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("severity", severity);
        event.put("source", "progressive-delivery-service");
        event.put("correlationId", correlationId);
        event.put("timestamp", Instant.now().toString());
        event.put("payload", payload);
        postQuiet(auditUrl + "/api/events", event);
        if (!"INFO".equals(severity)) {
            postQuiet(notificationUrl + "/api/notifications", Map.of(
                    "type", type,
                    "severity", severity,
                    "title", type.replace('_', ' '),
                    "message", "Progressive delivery action completed with status " + severity,
                    "correlationId", correlationId,
                    "payload", payload
            ));
        }
    }

    private void postQuiet(String url, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Object.class);
        } catch (Exception ignored) {
            // Runtime telemetry should never block the requested delivery action.
        }
    }
}
