package dev.nebulaops.gateway.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * v22.3 — Gateway proxy for endpoints not directly served by the gateway.
 *
 * Improvements over v22.3:
 *  - Added proxy routes for: /cost/**, /notifications/**, /audit/**, /secrets/**, /registry/**
 *  - Added pipeline/runs forward with pagination support
 *  - All proxy targets wired from application.yml (single source of truth)
 *  - Consistent graceful fallback: empty lists / error maps, never 500
 *  - PATCH forwarding uses HttpEntity to respect Content-Type
 */
@RestController
@RequestMapping("/api")
public class ProxyController {

    private final RestTemplate rest;

    public ProxyController(RestTemplate rest) {
        this.rest = rest;
    }

    @Value("${proxy.task}")           private String taskUrl;
    @Value("${proxy.ai-ops}")         private String aiOpsUrl;
    @Value("${proxy.auth}")           private String authUrl;
    @Value("${proxy.pipeline}")       private String pipelineUrl;
    @Value("${proxy.notification}")   private String notificationUrl;
    @Value("${proxy.cost}")           private String costUrl;
    @Value("${proxy.observability}")  private String observabilityUrl;
    @Value("${proxy.gitops}")         private String gitopsUrl;
    @Value("${proxy.environment}")    private String environmentUrl;
    @Value("${proxy.devsecops}")      private String devsecopsUrl;
    @Value("${proxy.release}")        private String releaseUrl;
    @Value("${proxy.policy}")         private String policyUrl;
    @Value("${proxy.audit}")          private String auditUrl;


    // ── Auth proxy ────────────────────────────────────────────────────────────

    @PostMapping("/auth/login")
    public ResponseEntity<Object> login(@RequestBody Object body) {
        return forward(() -> rest.postForObject(authUrl + "/api/auth/login", body, Object.class),
                       Map.of("error", "auth-service unavailable"));
    }

    @PostMapping("/auth/register")
    public ResponseEntity<Object> register(@RequestBody Object body) {
        return forward(() -> rest.postForObject(authUrl + "/api/auth/register", body, Object.class),
                       Map.of("error", "auth-service unavailable"));
    }

    // ── Tasks proxy ───────────────────────────────────────────────────────────

    @GetMapping("/tasks")
    public ResponseEntity<Object> listTasks(
            @RequestParam(required = false) String organizationId) {
        String url = taskUrl + "/api/tasks"
                   + (organizationId != null ? "?organizationId=" + organizationId : "");
        return forward(() -> rest.getForObject(url, Object.class), List.of());
    }

    @PostMapping("/tasks")
    public ResponseEntity<Object> createTask(@RequestBody Object body) {
        return forward(() -> rest.postForObject(taskUrl + "/api/tasks", body, Object.class),
                       Map.of("error", "task-service unavailable"));
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<Object> updateTask(@PathVariable String id, @RequestBody Object body) {
        return forward(() -> {
            rest.put(taskUrl + "/api/tasks/" + id, body);
            return Map.of("updated", id);
        }, Map.of("error", "task-service unavailable"));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Object> deleteTask(@PathVariable String id) {
        return forward(() -> {
            rest.delete(taskUrl + "/api/tasks/" + id);
            return Map.of("deleted", id);
        }, Map.of("error", "task-service unavailable"));
    }

    @PatchMapping("/tasks/{id}/status/{status}")
    public ResponseEntity<Object> updateStatus(@PathVariable String id, @PathVariable String status) {
        return forward(() -> {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            return rest.exchange(taskUrl + "/api/tasks/" + id + "/status/" + status,
                                 HttpMethod.PATCH,
                                 new HttpEntity<>("{}", h),
                                 Object.class).getBody();
        }, Map.of("error", "task-service unavailable"));
    }

    // ── AI Ops proxy ──────────────────────────────────────────────────────────

    @PostMapping("/ai-ops/analyze")
    public ResponseEntity<Object> analyze(@RequestBody(required = false) Object body) {
        return forward(() -> rest.postForObject(aiOpsUrl + "/api/ai-ops/analyze",
                                                body == null ? Map.of() : body, Object.class),
                       Map.of("live", false, "error", "ai-ops-service unavailable"));
    }

    @PostMapping("/ai-ops/autofix")
    public ResponseEntity<Object> autofix(@RequestBody(required = false) Object body) {
        return forward(() -> rest.postForObject(aiOpsUrl + "/api/ai-ops/autofix",
                                                body == null ? Map.of() : body, Object.class),
                       Map.of("live", false, "error", "ai-ops-service unavailable"));
    }


    @GetMapping("/ai-ops/incidents")
    public ResponseEntity<Object> aiIncidentsProxy() {
        return forward(() -> rest.getForObject(aiOpsUrl + "/api/ai-ops/incidents", Object.class), List.of());
    }

    @GetMapping("/ai-ops/incidents/{id}")
    public ResponseEntity<Object> aiIncidentDetailProxy(@PathVariable String id) {
        return forward(() -> rest.getForObject(aiOpsUrl + "/api/ai-ops/incidents/" + id, Object.class),
                       Map.of("id", id, "live", false, "error", "ai-ops-service unavailable"));
    }

    @PostMapping("/ai-ops/incidents/analyze")
    public ResponseEntity<Object> aiIncidentAnalyzeProxy(@RequestBody(required = false) Object body) {
        return forward(() -> rest.postForObject(aiOpsUrl + "/api/ai-ops/incidents/analyze",
                                                body == null ? Map.of() : body, Object.class),
                       Map.of("error", "ai-ops-service unavailable"));
    }

    @PostMapping("/ai-ops/incidents/{id}/runbook")
    public ResponseEntity<Object> aiIncidentRunbookProxy(@PathVariable String id) {
        return forward(() -> rest.postForObject(aiOpsUrl + "/api/ai-ops/incidents/" + id + "/runbook", Map.of(), Object.class),
                       Map.of("error", "ai-ops-service unavailable"));
    }

    @PostMapping("/ai-ops/incidents/{id}/create-task")
    public ResponseEntity<Object> aiIncidentCreateTaskProxy(@PathVariable String id) {
        return forward(() -> rest.postForObject(aiOpsUrl + "/api/ai-ops/incidents/" + id + "/create-task", Map.of(), Object.class),
                       Map.of("error", "ai-ops-service unavailable"));
    }

    // ── Pipeline proxy ────────────────────────────────────────────────────────

    @GetMapping("/pipeline/runs")
    public ResponseEntity<Object> pipelineRuns(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return forward(() -> rest.getForObject(
                pipelineUrl + "/api/pipeline/runs?page=" + page + "&size=" + size,
                Object.class), List.of());
    }

    @PostMapping("/pipeline/runs/{id}/trigger")
    public ResponseEntity<Object> triggerPipeline(@PathVariable String id) {
        return forward(() -> rest.postForObject(
                pipelineUrl + "/api/pipeline/runs/" + id + "/trigger", Map.of(), Object.class),
                Map.of("error", "pipeline-service unavailable"));
    }

    // ── Notifications proxy ───────────────────────────────────────────────────

    @GetMapping("/notifications/live")
    public ResponseEntity<Object> notificationsLive(
            @RequestParam(defaultValue = "50") int limit) {
        return forward(() -> rest.getForObject(
                notificationUrl + "/api/notifications?limit=" + limit, Object.class), List.of());
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Object> markRead(@PathVariable String id) {
        return forward(() -> {
            HttpHeaders h = new HttpHeaders();
            h.setContentType(MediaType.APPLICATION_JSON);
            return rest.exchange(notificationUrl + "/api/notifications/" + id + "/read",
                                 HttpMethod.PATCH, new HttpEntity<>("{}", h), Object.class).getBody();
        }, Map.of("error", "notification-service unavailable"));
    }


    @PostMapping("/notifications")
    public ResponseEntity<Object> createNotification(@RequestBody Object body) {
        return forward(() -> rest.postForObject(notificationUrl + "/api/notifications", body, Object.class),
                       Map.of("error", "notification-service unavailable"));
    }

    @GetMapping("/notifications/preferences")
    public ResponseEntity<Object> notificationPreferences() {
        return forward(() -> rest.getForObject(notificationUrl + "/api/notifications/preferences", Object.class),
                       Map.of("live", false, "items", List.of(), "error", "notification-service unavailable"));
    }

    @PostMapping("/notifications/preferences")
    public ResponseEntity<Object> updateNotificationPreferences(@RequestBody Object body) {
        return forward(() -> rest.postForObject(notificationUrl + "/api/notifications/preferences", body, Object.class),
                       Map.of("saved", false));
    }

    // ── Cost analytics proxy ──────────────────────────────────────────────────

    @GetMapping("/cost/summary")
    public ResponseEntity<Object> costSummary(
            @RequestParam(defaultValue = "monthly") String period) {
        return forward(() -> rest.getForObject(
                costUrl + "/api/cost/summary?period=" + period, Object.class),
                Map.of("monthly", 0, "delta", 0, "currency", "EUR",
                       "breakdown", List.of(), "live", false, "error", "cost-analytics-service unavailable"));
    }


    @GetMapping("/cost/services")
    public ResponseEntity<Object> costServicesProxy() {
        return forward(() -> rest.getForObject(costUrl + "/api/cost/services", Object.class), List.of());
    }

    @GetMapping("/cost/forecast")
    public ResponseEntity<Object> costForecastProxy() {
        return forward(() -> rest.getForObject(costUrl + "/api/cost/forecast", Object.class), Map.of("live", false));
    }

    @PostMapping("/cost/budget")
    public ResponseEntity<Object> costBudgetProxy(@RequestBody Object body) {
        return forward(() -> rest.postForObject(costUrl + "/api/cost/budget", body, Object.class), Map.of("saved", false));
    }

    @GetMapping("/cost/anomalies")
    public ResponseEntity<Object> costAnomaliesProxy() {
        return forward(() -> rest.getForObject(costUrl + "/api/cost/anomalies", Object.class), List.of());
    }

    @GetMapping("/cost/recommendations")
    public ResponseEntity<Object> costRecommendationsProxy() {
        return forward(() -> rest.getForObject(costUrl + "/api/cost/recommendations", Object.class), List.of());
    }

    // ── Audit proxy ───────────────────────────────────────────────────────────

    @GetMapping("/audit/events")
    public ResponseEntity<Object> auditEvents(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false)     String actor,
            @RequestParam(required = false)     String action) {
        StringBuilder url = new StringBuilder(auditUrl + "/api/audit/events?limit=" + limit);
        if (actor  != null) url.append("&actor=").append(actor);
        if (action != null) url.append("&action=").append(action);
        return forward(() -> rest.getForObject(url.toString(), Object.class), List.of());
    }


    @PostMapping("/devsecops/scan/image")
    public ResponseEntity<Object> devsecopsImageScanProxy(@RequestBody(required = false) Object body) {
        return forward(() -> rest.postForObject(devsecopsUrl + "/api/devsecops/scan/image",
                                                body == null ? Map.of() : body, Object.class),
                       Map.of("live", false, "error", "devsecops-service unavailable"));
    }

    @PostMapping("/devsecops/scan/repository")
    public ResponseEntity<Object> devsecopsRepositoryScanProxy(@RequestBody(required = false) Object body) {
        return forward(() -> rest.postForObject(devsecopsUrl + "/api/devsecops/scan/repository",
                                                body == null ? Map.of() : body, Object.class),
                       Map.of("live", false, "error", "devsecops-service unavailable"));
    }

    @GetMapping("/devsecops/vulnerabilities")
    public ResponseEntity<Object> devsecopsVulnerabilitiesProxy() {
        return forward(() -> rest.getForObject(devsecopsUrl + "/api/devsecops/vulnerabilities", Object.class), List.of());
    }

    @GetMapping("/devsecops/sbom/{image}")
    public ResponseEntity<Object> devsecopsSbomProxy(@PathVariable String image) {
        return forward(() -> rest.getForObject(devsecopsUrl + "/api/devsecops/sbom/" + image, Object.class),
                       Map.of("image", image, "live", false));
    }

    @GetMapping("/devsecops/reports/{id}")
    public ResponseEntity<Object> devsecopsReportProxy(@PathVariable String id) {
        return forward(() -> rest.getForObject(devsecopsUrl + "/api/devsecops/reports/" + id, Object.class),
                       Map.of("id", id, "live", false, "error", "devsecops-service unavailable"));
    }

    // ── Secrets proxy ─────────────────────────────────────────────────────────

    @GetMapping("/secrets/list")
    public ResponseEntity<Object> secretsList(
            @RequestParam(required = false) String namespace) {
        String url = devsecopsUrl + "/api/secrets"
                   + (namespace != null ? "?namespace=" + namespace : "");
        return forward(() -> rest.getForObject(url, Object.class), List.of());
    }

    // ── Registry proxy ────────────────────────────────────────────────────────

    @GetMapping("/registry/images")
    public ResponseEntity<Object> registryImages(
            @RequestParam(required = false) String repository) {
        String url = devsecopsUrl + "/api/registry/images"
                   + (repository != null ? "?repository=" + repository : "");
        return forward(() -> rest.getForObject(url, Object.class), List.of());
    }


    // ── Release Center proxy ──────────────────────────────────────────────────

    @GetMapping("/releases")
    public ResponseEntity<Object> releasesProxy() {
        return forward(() -> rest.getForObject(releaseUrl + "/api/releases", Object.class), List.of());
    }

    @GetMapping("/releases/{id}")
    public ResponseEntity<Object> releaseDetail(@PathVariable String id) {
        return forward(() -> rest.getForObject(releaseUrl + "/api/releases/" + id, Object.class),
                       Map.of("id", id, "live", false, "error", "release-orchestrator unavailable"));
    }

    @PostMapping("/releases")
    public ResponseEntity<Object> createRelease(@RequestBody Object body) {
        return forward(() -> rest.postForObject(releaseUrl + "/api/releases", body, Object.class),
                       Map.of("error", "release-orchestrator unavailable"));
    }

    @PostMapping("/releases/{id}/promote")
    public ResponseEntity<Object> promoteRelease(@PathVariable String id, @RequestBody(required = false) Object body) {
        return forward(() -> rest.postForObject(releaseUrl + "/api/releases/" + id + "/promote",
                                                body == null ? Map.of() : body, Object.class),
                       Map.of("error", "release-orchestrator unavailable"));
    }

    @PostMapping("/releases/{id}/rollback")
    public ResponseEntity<Object> rollbackRelease(@PathVariable String id, @RequestBody(required = false) Object body) {
        return forward(() -> rest.postForObject(releaseUrl + "/api/releases/" + id + "/rollback",
                                                body == null ? Map.of() : body, Object.class),
                       Map.of("error", "release-orchestrator unavailable"));
    }

    @GetMapping("/releases/{id}/timeline")
    public ResponseEntity<Object> releaseTimeline(@PathVariable String id) {
        return forward(() -> rest.getForObject(releaseUrl + "/api/releases/" + id + "/timeline", Object.class), List.of());
    }

    @GetMapping("/releases/{id}/health-impact")
    public ResponseEntity<Object> releaseHealthImpact(@PathVariable String id) {
        return forward(() -> rest.getForObject(releaseUrl + "/api/releases/" + id + "/health-impact", Object.class),
                       Map.of("id", id, "live", false, "error", "release-orchestrator unavailable"));
    }

    // ── Policy Center proxy ───────────────────────────────────────────────────

    @GetMapping("/policies")
    public ResponseEntity<Object> policiesProxy() {
        return forward(() -> rest.getForObject(policyUrl + "/api/policies", Object.class), List.of());
    }

    @PostMapping("/policies")
    public ResponseEntity<Object> createPolicy(@RequestBody Object body) {
        return forward(() -> rest.postForObject(policyUrl + "/api/policies", body, Object.class),
                       Map.of("error", "policy-governance unavailable"));
    }

    @PutMapping("/policies/{id}")
    public ResponseEntity<Object> updatePolicy(@PathVariable String id, @RequestBody Object body) {
        return forward(() -> rest.exchange(policyUrl + "/api/policies/" + id,
                                           HttpMethod.PUT, new HttpEntity<>(body), Object.class).getBody(),
                       Map.of("error", "policy-governance unavailable"));
    }

    @DeleteMapping("/policies/{id}")
    public ResponseEntity<Object> deletePolicy(@PathVariable String id) {
        return forward(() -> {
            rest.delete(policyUrl + "/api/policies/" + id);
            return Map.of("deleted", id);
        }, Map.of("error", "policy-governance unavailable"));
    }

    @PostMapping("/policies/evaluate")
    public ResponseEntity<Object> evaluatePolicy(@RequestBody(required = false) Object body) {
        return forward(() -> rest.postForObject(policyUrl + "/api/policies/evaluate",
                                                body == null ? Map.of() : body, Object.class),
                       Map.of("live", false, "error", "policy-governance unavailable"));
    }

    @GetMapping("/policies/evaluations")
    public ResponseEntity<Object> policyEvaluations() {
        return forward(() -> rest.getForObject(policyUrl + "/api/policies/evaluations", Object.class), List.of());
    }

    // ── Platform events proxy ─────────────────────────────────────────────────

    @GetMapping("/events")
    public ResponseEntity<Object> platformEvents(@RequestParam(defaultValue = "100") int limit,
                                                 @RequestParam(required = false) String correlationId) {
        String url = auditUrl + "/api/events?limit=" + limit + (correlationId != null ? "&correlationId=" + correlationId : "");
        return forward(() -> rest.getForObject(url, Object.class), List.of());
    }

    @PostMapping("/events")
    public ResponseEntity<Object> recordPlatformEvent(@RequestBody Object body) {
        return forward(() -> rest.postForObject(auditUrl + "/api/events", body, Object.class),
                       Map.of("error", "audit-service unavailable"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    @FunctionalInterface
    interface Call { Object run() throws Exception; }

    private ResponseEntity<Object> forward(Call call, Object fallback) {
        try {
            Object result = call.run();
            return ResponseEntity.ok(result != null ? result : fallback);
        } catch (ResourceAccessException e) {
            return ResponseEntity.ok(fallback);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode())
                                 .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(fallback);
        }
    }
}
