package dev.nebulaops.release.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/releases")
@SuppressWarnings({"unchecked", "rawtypes"})
public class ReleaseOrchestratorController {
    private final RestTemplate rest = new RestTemplate();
    private final List<Map<String,Object>> createdReleases = new ArrayList<>();

    private final String gitlabUrl;
    private final String gitlabToken;
    private final String gitlabProjectId;
    private final String argoUrl;
    private final String argoToken;
    private final String gatewayUrl;
    private final String policyUrl;
    private final String auditUrl;
    private final String notificationUrl;

    public ReleaseOrchestratorController(
        @Value("${nebulaops.gitlab.url:http://gitlab:80}") String gitlabUrl,
        @Value("${nebulaops.gitlab.token:}") String gitlabToken,
        @Value("${nebulaops.gitlab.project-id:}") String gitlabProjectId,
        @Value("${nebulaops.argocd.url:http://argocd-server.argocd.svc.cluster.local}") String argoUrl,
        @Value("${nebulaops.argocd.token:}") String argoToken,
        @Value("${nebulaops.gateway.url:http://gateway-service:8080}") String gatewayUrl,
        @Value("${nebulaops.policy.url:http://policy-governance-service:8100}") String policyUrl,
        @Value("${nebulaops.audit.url:http://audit-service:8101}") String auditUrl,
        @Value("${nebulaops.notification.url:http://notification-service:8083}") String notificationUrl
    ) {
        this.gitlabUrl = gitlabUrl;
        this.gitlabToken = gitlabToken;
        this.gitlabProjectId = gitlabProjectId;
        this.argoUrl = argoUrl;
        this.argoToken = argoToken;
        this.gatewayUrl = gatewayUrl;
        this.policyUrl = policyUrl;
        this.auditUrl = auditUrl;
        this.notificationUrl = notificationUrl;
    }

    @GetMapping
    public ResponseEntity<Object> list(@RequestParam(defaultValue = "local") String environment) {
        List<Map<String, Object>> releases = new ArrayList<>();
        releases.addAll(createdReleases);
        releases.addAll(discoverGitLabPipelines());
        releases.addAll(discoverArgoApplications());

        return ResponseEntity.ok(Map.of(
            "environment", environment,
            "count", releases.size(),
            "items", releases,
            "live", !releases.isEmpty(),
            "toolStatus", releases.isEmpty()
                ? "No releases discovered. Configure GITLAB_PROJECT_ID/GITLAB_TOKEN or ARGOCD_URL/ARGOCD_TOKEN, or create a release through POST /api/releases."
                : "Release list built from live GitLab/ArgoCD signals and runtime-created releases"
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> detail(@PathVariable String id) {
        Optional<Map<String, Object>> created = createdReleases.stream().filter(r -> id.equals(r.get("id"))).findFirst();
        if (created.isPresent()) return ResponseEntity.ok(enrich(created.get()));
        return ResponseEntity.ok(Map.of("id", id, "status", "NOT_FOUND", "live", false,
            "toolStatus", "Release not found in runtime-created releases. Check GitLab/ArgoCD discovery configuration."));
    }

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody Map<String,Object> body) {
        String app = String.valueOf(body.getOrDefault("application", "nebulaops-service"));
        String version = String.valueOf(body.getOrDefault("version", "23.4.0"));
        Map<String,Object> r = new LinkedHashMap<>();
        r.put("id", "rel-" + UUID.randomUUID());
        r.put("application", app);
        r.put("version", version);
        r.put("status", "CREATED");
        r.put("image", body.getOrDefault("image", app + ":" + version));
        r.put("environment", body.getOrDefault("environment", "local"));
        r.put("createdAt", Instant.now().toString());
        createdReleases.add(0, r);
        publish("RELEASE_CREATED", "INFO", String.valueOf(r.get("id")), Map.of("release", r));
        return ResponseEntity.ok(enrich(r));
    }

    @PostMapping("/{id}/promote")
    public ResponseEntity<Object> promote(@PathVariable String id, @RequestBody(required=false) Map<String,Object> body) {
        Map<String, Object> release = mutableRelease(id);
        if (release == null) return ResponseEntity.ok(Map.of("id", id, "status", "NOT_FOUND", "live", false));

        Map<String, Object> evaluation = policyEvaluate(release, body == null ? Map.of() : body);
        boolean allowed = Boolean.TRUE.equals(evaluation.get("allowPromotion")) || "PASS".equals(String.valueOf(evaluation.get("status")));
        String correlationId = "corr-" + UUID.randomUUID();

        if (!allowed) {
            release.put("status", "BLOCKED_BY_POLICY");
            release.put("policyEvaluation", evaluation);
            publish("RELEASE_PROMOTION_BLOCKED", "WARN", correlationId, Map.of("release", release, "policyEvaluation", evaluation));
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", id);
            response.put("action", "PROMOTE");
            response.put("status", "BLOCKED_BY_POLICY");
            response.put("allowPromotion", false);
            response.put("policyEvaluation", evaluation);
            response.put("correlationId", correlationId);
            return ResponseEntity.ok(response);
        }

        Map<String, Object> sync = argoSync(String.valueOf(release.get("application")));
        Map<String, Object> health = kubernetesHealth(String.valueOf(release.get("application")));
        release.put("status", "PROMOTED");
        release.put("gitops", sync);
        release.put("kubernetes", health);
        release.put("promotedAt", Instant.now().toString());
        release.put("policyEvaluation", evaluation);
        publish("RELEASE_PROMOTED", "INFO", correlationId, Map.of("release", release, "gitops", sync, "kubernetes", health));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("action", "PROMOTE");
        response.put("status", "PROMOTED");
        response.put("targetEnvironment", body == null ? "local" : body.getOrDefault("targetEnvironment", "local"));
        response.put("policyEvaluation", evaluation);
        response.put("gitops", sync);
        response.put("kubernetes", health);
        response.put("correlationId", correlationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/rollback")
    public ResponseEntity<Object> rollback(@PathVariable String id, @RequestBody(required=false) Map<String,Object> body) {
        Map<String, Object> release = mutableRelease(id);
        if (release == null) return ResponseEntity.ok(Map.of("id", id, "status", "NOT_FOUND", "live", false));

        String revision = String.valueOf(body == null ? "previous" : body.getOrDefault("revision", "previous"));
        Map<String, Object> rollback = argoRollback(String.valueOf(release.get("application")), revision);
        release.put("status", "ROLLBACK_QUEUED");
        release.put("rollback", rollback);
        release.put("rollbackAt", Instant.now().toString());

        String correlationId = "corr-" + UUID.randomUUID();
        publish("RELEASE_ROLLBACK_REQUESTED", "WARN", correlationId, Map.of("release", release, "rollback", rollback));
        return ResponseEntity.ok(Map.of("id", id, "action", "ROLLBACK", "status", "ROLLBACK_QUEUED",
            "revision", revision, "gitops", rollback, "correlationId", correlationId));
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<Object> timelineEndpoint(@PathVariable String id) {
        Map<String, Object> release = mutableRelease(id);
        return ResponseEntity.ok(Map.of("id", id, "items", timeline(id, release), "live", release != null));
    }

    @GetMapping("/{id}/health-impact")
    public ResponseEntity<Object> healthImpact(@PathVariable String id) {
        Map<String, Object> release = mutableRelease(id);
        String app = release == null ? id : String.valueOf(release.get("application"));
        Map<String, Object> health = kubernetesHealth(app);
        Map<String, Object> cost = get(gatewayUrl + "/api/cost/forecast", Map.of("live", false, "toolStatus", "cost forecast unavailable"));
        return ResponseEntity.ok(Map.of("id", id, "services", List.of(app),
            "risk", Boolean.TRUE.equals(health.get("healthy")) ? "LOW" : "UNKNOWN",
            "estimatedCostDelta", cost.getOrDefault("forecast", "unknown"),
            "sloImpact", Boolean.TRUE.equals(health.get("healthy")) ? "none" : "unknown",
            "kubernetes", health, "cost", cost, "live", true));
    }

    private List<Map<String, Object>> discoverGitLabPipelines() {
        if (gitlabProjectId == null || gitlabProjectId.isBlank()) return List.of();
        Object pipelines = getGitLab("/api/v4/projects/" + gitlabProjectId + "/pipelines?per_page=20").get("items");
        if (!(pipelines instanceof List list)) return List.of();
        List<Map<String, Object>> releases = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map p)) continue;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", "gitlab-pipeline-" + p.getOrDefault("id", UUID.randomUUID()));
            r.put("application", "gitlab-project-" + gitlabProjectId);
            r.put("version", p.getOrDefault("sha", "unknown"));
            r.put("status", p.getOrDefault("status", "UNKNOWN"));
            r.put("source", "gitlab");
            r.put("pipeline", p);
            r.put("createdAt", p.getOrDefault("created_at", Instant.now().toString()));
            releases.add(r);
        }
        return releases;
    }

    private List<Map<String, Object>> discoverArgoApplications() {
        Map<String, Object> response = argoListApplications();
        Object items = response.get("items");
        if (!(items instanceof List list)) return List.of();
        List<Map<String, Object>> releases = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map app)) continue;
            Map metadata = app.get("metadata") instanceof Map ? (Map) app.get("metadata") : Map.of();
            Map status = app.get("status") instanceof Map ? (Map) app.get("status") : Map.of();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", "argocd-app-" + metadata.getOrDefault("name", UUID.randomUUID()));
            r.put("application", metadata.getOrDefault("name", "argocd-app"));
            r.put("version", "argocd");
            r.put("status", status.getOrDefault("sync", Map.of()));
            r.put("source", "argocd");
            r.put("gitops", app);
            r.put("createdAt", metadata.getOrDefault("creationTimestamp", Instant.now().toString()));
            releases.add(r);
        }
        return releases;
    }

    private Map<String, Object> enrich(Map<String, Object> release) {
        Map<String, Object> r = new LinkedHashMap<>(release);
        r.put("pipeline", gitlabPipeline(String.valueOf(release.get("application"))));
        r.put("gitops", argoSync(String.valueOf(release.get("application"))));
        r.put("kubernetes", kubernetesHealth(String.valueOf(release.get("application"))));
        r.put("timeline", timeline(String.valueOf(release.get("id")), release));
        return r;
    }

    private Map<String, Object> mutableRelease(String id) {
        return createdReleases.stream().filter(r -> id.equals(r.get("id"))).findFirst().orElse(null);
    }

    private List<Map<String, Object>> timeline(String id, Map<String, Object> release) {
        if (release == null) return List.of();
        return List.of(
            Map.of("step","created", "status", release.getOrDefault("status", "UNKNOWN"), "time", release.getOrDefault("createdAt", Instant.now().toString())),
            Map.of("step","policy-gate", "status", release.containsKey("policyEvaluation") ? ((Map) release.get("policyEvaluation")).getOrDefault("status", "UNKNOWN") : "NOT_EVALUATED"),
            Map.of("step","gitops-sync", "status", release.containsKey("gitops") ? "CHECKED" : "NOT_CHECKED"),
            Map.of("step","kubernetes-health", "status", release.containsKey("kubernetes") ? "CHECKED" : "NOT_CHECKED")
        );
    }

    private Map<String, Object> policyEvaluate(Map<String, Object> release, Map<String, Object> request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("target", release.get("application"));
        payload.put("release", release);
        payload.put("request", request);
        return post(policyUrl + "/api/policies/evaluate", payload, Map.of("status", "WARN", "allowPromotion", false, "live", false, "toolStatus", "Policy Center unavailable"));
    }

    private Map<String, Object> gitlabPipeline(String app) {
        if (gitlabProjectId == null || gitlabProjectId.isBlank()) {
            return Map.of("live", false, "toolStatus", "GITLAB_PROJECT_ID not configured", "application", app);
        }
        return getGitLab("/api/v4/projects/" + gitlabProjectId + "/pipelines?per_page=1");
    }

    private Map<String, Object> getGitLab(String path) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (gitlabToken != null && !gitlabToken.isBlank()) headers.set("PRIVATE-TOKEN", gitlabToken);
            ResponseEntity<Object> response = rest.exchange(gitlabUrl + path, HttpMethod.GET, new HttpEntity<>(headers), Object.class);
            Object body = response.getBody();
            if (body instanceof Map) return (Map<String, Object>) body;
            return Map.of("live", true, "items", body == null ? List.of() : body);
        } catch (Exception e) {
            return Map.of("live", false, "toolStatus", "GitLab unavailable", "error", e.getMessage());
        }
    }

    private Map<String, Object> argoListApplications() {
        return getArgo("/api/v1/applications");
    }

    private Map<String, Object> argoSync(String app) {
        return getArgo("/api/v1/applications/" + app);
    }

    private Map<String, Object> argoRollback(String app, String revision) {
        return postArgo("/api/v1/applications/" + app + "/rollback", Map.of("id", revision));
    }

    private Map<String, Object> getArgo(String path) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (argoToken != null && !argoToken.isBlank()) headers.setBearerAuth(argoToken);
            ResponseEntity<Object> response = rest.exchange(argoUrl + path, HttpMethod.GET, new HttpEntity<>(headers), Object.class);
            Object body = response.getBody();
            if (body instanceof Map) return (Map<String, Object>) body;
            return Map.of("live", true, "items", body == null ? List.of() : body);
        } catch (Exception e) {
            return Map.of("live", false, "toolStatus", "ArgoCD unavailable", "error", e.getMessage());
        }
    }

    private Map<String, Object> postArgo(String path, Object payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (argoToken != null && !argoToken.isBlank()) headers.setBearerAuth(argoToken);
            ResponseEntity<Object> response = rest.exchange(argoUrl + path, HttpMethod.POST, new HttpEntity<>(payload, headers), Object.class);
            Object body = response.getBody();
            if (body instanceof Map) return (Map<String, Object>) body;
            return Map.of("live", true, "result", body == null ? Map.of() : body);
        } catch (Exception e) {
            return Map.of("live", false, "toolStatus", "ArgoCD rollback unavailable", "error", e.getMessage());
        }
    }

    private Map<String, Object> kubernetesHealth(String app) {
        Map<String, Object> snapshot = get(gatewayUrl + "/api/kubernetes/snapshot", Map.of("live", false, "toolStatus", "Kubernetes unavailable"));
        boolean healthy = !String.valueOf(snapshot).toLowerCase(Locale.ROOT).contains("crashloopbackoff")
                       && !String.valueOf(snapshot).toLowerCase(Locale.ROOT).contains("imagepullbackoff");
        return Map.of("application", app, "healthy", healthy, "snapshot", snapshot, "live", Boolean.TRUE.equals(snapshot.getOrDefault("live", true)));
    }

    private void publish(String type, String severity, String correlationId, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("source", "release-orchestrator-service");
        event.put("actor", "operator");
        event.put("severity", severity);
        event.put("correlationId", correlationId);
        event.put("payload", payload);
        post(auditUrl + "/api/events", event, Map.of());
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("type", type);
        notification.put("source", "release-orchestrator-service");
        notification.put("severity", severity);
        notification.put("message", type + " " + correlationId);
        notification.put("payload", payload);
        post(notificationUrl + "/api/notifications", notification, Map.of());
    }

    private Map<String, Object> get(String url, Map<String, Object> fallback) {
        try {
            Object body = rest.getForObject(url, Object.class);
            if (body instanceof Map) return (Map<String, Object>) body;
            return Map.of("live", true, "items", body == null ? List.of() : body);
        } catch (Exception e) {
            Map<String, Object> m = new LinkedHashMap<>(fallback);
            m.put("error", e.getMessage());
            return m;
        }
    }

    private Map<String, Object> post(String url, Object payload, Map<String, Object> fallback) {
        try {
            Object body = rest.postForObject(url, payload, Object.class);
            if (body instanceof Map) return (Map<String, Object>) body;
            return Map.of("live", true, "result", body == null ? Map.of() : body);
        } catch (Exception e) {
            Map<String, Object> m = new LinkedHashMap<>(fallback);
            m.put("error", e.getMessage());
            return m;
        }
    }
}
