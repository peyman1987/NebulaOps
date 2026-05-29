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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * v24.1 — Gateway proxy for endpoints not directly served by the gateway.
 *
 * Improvements over v24.1:
 *  - Added proxy routes for: /cost/**, /notifications/**, /audit/**, /secrets/**, /registry/**
 *  - Added pipeline/runs forward with pagination support
 *  - All proxy targets wired from application.yml (single source of truth)
 *  - Consistent degraded responses: empty lists / error maps, never synthetic rows or 500
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
    @Value("${proxy.progressive-delivery}") private String progressiveDeliveryUrl;
    @Value("${proxy.audit}")          private String auditUrl;
    @Value("${nebulaops.default-organization-id:nebulaops}") private String defaultOrganizationId;


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

    // ── Identity / Keycloak admin proxy ───────────────────────────────────────

    @GetMapping("/identity/realms/{realm}/status")
    public ResponseEntity<Object> identityRealmStatus(@PathVariable String realm) {
        return forward(() -> rest.getForObject(authUrl + "/api/identity/realms/" + realm + "/status", Object.class),
                Map.of("live", false, "state", "KEYCLOAK_REALM_UNAVAILABLE", "realm", realm));
    }

    @GetMapping("/identity/realms/{realm}/cache/status")
    public ResponseEntity<Object> identityRealmCacheStatus(@PathVariable String realm) {
        return forward(() -> rest.getForObject(authUrl + "/api/identity/realms/" + realm + "/cache/status", Object.class),
                Map.of("live", false, "state", "REDIS_IDENTITY_CACHE_UNAVAILABLE", "realm", realm));
    }

    @GetMapping("/identity/cache/status")
    public ResponseEntity<Object> identityDefaultCacheStatus() {
        String realm = System.getenv().getOrDefault("KEYCLOAK_REALM", "nebulaops");
        return forward(() -> rest.getForObject(authUrl + "/api/identity/realms/" + realm + "/cache/status", Object.class),
                Map.of("live", false, "state", "REDIS_IDENTITY_CACHE_UNAVAILABLE", "realm", realm));
    }

    @GetMapping("/identity/realms/{realm}/users")
    public ResponseEntity<Object> identityUsers(@PathVariable String realm,
                                                @RequestParam(required = false) String search) {
        String url = authUrl + "/api/identity/realms/" + realm + "/users" + (search == null ? "" : "?search=" + enc(search));
        return forward(() -> rest.getForObject(url, Object.class), List.of());
    }

    @PostMapping("/identity/realms/{realm}/users")
    public ResponseEntity<Object> identityCreateUser(@PathVariable String realm, @RequestBody Object body) {
        return forward(() -> rest.postForObject(authUrl + "/api/identity/realms/" + realm + "/users", body, Object.class),
                Map.of("error", "identity admin unavailable"));
    }

    @PutMapping("/identity/realms/{realm}/users/{id}")
    public ResponseEntity<Object> identityUpdateUser(@PathVariable String realm, @PathVariable String id, @RequestBody Object body) {
        return forward(() -> rest.exchange(authUrl + "/api/identity/realms/" + realm + "/users/" + id,
                HttpMethod.PUT, jsonEntity(body), Object.class).getBody(), Map.of("error", "identity admin unavailable"));
    }

    @PatchMapping("/identity/realms/{realm}/users/{id}/disable")
    public ResponseEntity<Object> identityDisableUser(@PathVariable String realm, @PathVariable String id) {
        return forward(() -> rest.exchange(authUrl + "/api/identity/realms/" + realm + "/users/" + id + "/disable",
                HttpMethod.PATCH, jsonEntity(Map.of()), Object.class).getBody(), Map.of("error", "identity admin unavailable"));
    }

    @GetMapping("/identity/realms/{realm}/groups")
    public ResponseEntity<Object> identityGroups(@PathVariable String realm) {
        return forward(() -> rest.getForObject(authUrl + "/api/identity/realms/" + realm + "/groups", Object.class), List.of());
    }

    @PostMapping("/identity/realms/{realm}/groups")
    public ResponseEntity<Object> identityCreateGroup(@PathVariable String realm, @RequestBody Object body) {
        return forward(() -> rest.postForObject(authUrl + "/api/identity/realms/" + realm + "/groups", body, Object.class),
                Map.of("error", "identity admin unavailable"));
    }

    @PutMapping("/identity/realms/{realm}/groups/{id}")
    public ResponseEntity<Object> identityUpdateGroup(@PathVariable String realm, @PathVariable String id, @RequestBody Object body) {
        return forward(() -> rest.exchange(authUrl + "/api/identity/realms/" + realm + "/groups/" + id,
                HttpMethod.PUT, jsonEntity(body), Object.class).getBody(), Map.of("error", "identity admin unavailable"));
    }

    @PatchMapping("/identity/realms/{realm}/groups/{id}/disable")
    public ResponseEntity<Object> identityDisableGroup(@PathVariable String realm, @PathVariable String id) {
        return forward(() -> rest.exchange(authUrl + "/api/identity/realms/" + realm + "/groups/" + id + "/disable",
                HttpMethod.PATCH, jsonEntity(Map.of()), Object.class).getBody(), Map.of("error", "identity admin unavailable"));
    }

    @GetMapping("/identity/realms/{realm}/roles")
    public ResponseEntity<Object> identityRoles(@PathVariable String realm) {
        return forward(() -> rest.getForObject(authUrl + "/api/identity/realms/" + realm + "/roles", Object.class), List.of());
    }

    @PostMapping("/identity/realms/{realm}/roles")
    public ResponseEntity<Object> identityCreateRole(@PathVariable String realm, @RequestBody Object body) {
        return forward(() -> rest.postForObject(authUrl + "/api/identity/realms/" + realm + "/roles", body, Object.class),
                Map.of("error", "identity admin unavailable"));
    }

    @PutMapping("/identity/realms/{realm}/roles/{name}")
    public ResponseEntity<Object> identityUpdateRole(@PathVariable String realm, @PathVariable String name, @RequestBody Object body) {
        return forward(() -> rest.exchange(authUrl + "/api/identity/realms/" + realm + "/roles/" + name,
                HttpMethod.PUT, jsonEntity(body), Object.class).getBody(), Map.of("error", "identity admin unavailable"));
    }

    @PatchMapping("/identity/realms/{realm}/roles/{name}/disable")
    public ResponseEntity<Object> identityDisableRole(@PathVariable String realm, @PathVariable String name) {
        return forward(() -> rest.exchange(authUrl + "/api/identity/realms/" + realm + "/roles/" + name + "/disable",
                HttpMethod.PATCH, jsonEntity(Map.of()), Object.class).getBody(), Map.of("error", "identity admin unavailable"));
    }


    // ── Tasks proxy ───────────────────────────────────────────────────────────

    @GetMapping("/tasks")
    public ResponseEntity<Object> listTasks(
            @RequestParam(required = false) String organizationId) {
        String url = taskUrl + "/api/tasks"
                   + (organizationId != null ? "?organizationId=" + enc(resolveOrganization(organizationId)) : "");
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

    @PatchMapping("/tasks/{id}/move")
    public ResponseEntity<Object> moveTask(@PathVariable String id, @RequestBody(required = false) Object body) {
        return forward(() -> rest.exchange(taskUrl + "/api/tasks/" + id + "/move",
                                 HttpMethod.PATCH,
                                 jsonEntity(body == null ? Map.of() : body),
                                 Object.class).getBody(),
                       Map.of("error", "task-service unavailable"));
    }


    // ── Observability & Audit Center proxy ───────────────────────────────────

    @GetMapping({"/observability", "/observability/stack"})
    public ResponseEntity<Object> observabilityStack() {
        return forward(() -> rest.getForObject(observabilityUrl + "/api/observability", Object.class),
                Map.of("live", false, "items", List.of(), "error", "observability-service unavailable"));
    }

    @GetMapping("/observability/overview")
    public ResponseEntity<Object> observabilityOverview(@RequestParam(required = false) String organizationId) {
        return forward(() -> rest.getForObject(observabilityUrl + "/api/observability/overview?organizationId=" + enc(resolveOrganization(organizationId)), Object.class),
                Map.of("live", false, "items", List.of(), "error", "observability-service unavailable"));
    }

    @GetMapping("/observability/services")
    public ResponseEntity<Object> observabilityServices() {
        return forward(() -> rest.getForObject(observabilityUrl + "/api/observability/services", Object.class), List.of());
    }

    @GetMapping("/observability/metrics/prometheus")
    public ResponseEntity<Object> observabilityPrometheus(@RequestParam(defaultValue = "up") String query) {
        return forward(() -> rest.getForObject(observabilityUrl + "/api/observability/metrics/prometheus?query=" + enc(query), Object.class), List.of());
    }

    @GetMapping("/observability/logs/loki")
    public ResponseEntity<Object> observabilityLoki(@RequestParam(defaultValue = "{job=~\".+\"}") String query) {
        return forward(() -> rest.getForObject(observabilityUrl + "/api/observability/logs/loki?query=" + enc(query), Object.class), List.of());
    }

    @GetMapping("/observability/traces/tempo")
    public ResponseEntity<Object> observabilityTempo(@RequestParam(defaultValue = "20") int limit) {
        return forward(() -> rest.getForObject(observabilityUrl + "/api/observability/traces/tempo?limit=" + limit, Object.class), List.of());
    }

    @GetMapping("/observability/audit/events")
    public ResponseEntity<Object> observabilityAudit(@RequestParam(defaultValue = "100") int limit) {
        return forward(() -> rest.getForObject(observabilityUrl + "/api/observability/audit/events?limit=" + limit, Object.class), List.of());
    }

    @GetMapping("/observability/events/notifications")
    public ResponseEntity<Object> observabilityNotifications(@RequestParam(defaultValue = "100") int limit) {
        return forward(() -> rest.getForObject(observabilityUrl + "/api/observability/events/notifications?limit=" + limit, Object.class), List.of());
    }

    @GetMapping("/observability/events/tasks")
    public ResponseEntity<Object> observabilityTasks(@RequestParam(required = false) String organizationId) {
        return forward(() -> rest.getForObject(observabilityUrl + "/api/observability/events/tasks?organizationId=" + enc(resolveOrganization(organizationId)), Object.class), List.of());
    }

    @GetMapping("/observability/events/rabbitmq")
    public ResponseEntity<Object> observabilityRabbitmq() {
        return forward(() -> rest.getForObject(observabilityUrl + "/api/observability/events/rabbitmq", Object.class), List.of());
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
            @RequestParam(required = false)     String action,
            @RequestParam(required = false)     String type,
            @RequestParam(required = false)     String correlationId) {
        StringBuilder url = new StringBuilder(auditUrl + "/api/audit/events?limit=" + limit);
        if (actor != null) url.append("&actor=").append(enc(actor));
        if (correlationId != null) url.append("&correlationId=").append(enc(correlationId));
        String eventType = type != null ? type : action;
        if (eventType != null) url.append("&type=").append(enc(eventType));
        return forward(() -> rest.getForObject(url.toString(), Object.class), List.of());
    }

    @PostMapping("/audit/events")
    public ResponseEntity<Object> recordAuditEvent(@RequestBody Object body) {
        return forward(() -> rest.postForObject(auditUrl + "/api/audit/events", body, Object.class),
                       Map.of("error", "audit-service unavailable"));
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

    // ── Policy, Approval & Governance Center proxy ────────────────────────────

    @GetMapping("/governance")
    public ResponseEntity<Object> governanceSummary() {
        return forward(() -> rest.getForObject(policyUrl + "/api/governance", Object.class),
                       Map.of("live", false, "realDataOnly", true, "error", "policy-governance unavailable"));
    }

    @GetMapping({"/governance/policies", "/policies"})
    public ResponseEntity<Object> policiesProxy() {
        return forward(() -> rest.getForObject(policyUrl + "/api/governance/policies", Object.class),
                       Map.of("live", false, "realDataOnly", true, "items", List.of(), "error", "policy-governance unavailable"));
    }

    @PostMapping({"/governance/policies", "/policies"})
    public ResponseEntity<Object> createPolicy(@RequestBody Object body) {
        return forward(() -> rest.postForObject(policyUrl + "/api/governance/policies", body, Object.class),
                       Map.of("live", false, "realDataOnly", true, "error", "policy-governance unavailable"));
    }

    @PutMapping({"/governance/policies/{id}", "/policies/{id}"})
    public ResponseEntity<Object> updatePolicy(@PathVariable String id, @RequestBody Object body) {
        return forward(() -> rest.exchange(policyUrl + "/api/governance/policies/" + id,
                                           HttpMethod.PUT, jsonEntity(body), Object.class).getBody(),
                       Map.of("live", false, "realDataOnly", true, "error", "policy-governance unavailable"));
    }

    @DeleteMapping({"/governance/policies/{id}", "/policies/{id}"})
    public ResponseEntity<Object> deletePolicy(@PathVariable String id) {
        return forward(() -> rest.exchange(policyUrl + "/api/governance/policies/" + id,
                                           HttpMethod.DELETE, jsonEntity(Map.of()), Object.class).getBody(),
                       Map.of("live", false, "realDataOnly", true, "error", "policy-governance unavailable"));
    }

    @PostMapping({"/governance/decisions", "/policies/evaluate"})
    public ResponseEntity<Object> governanceDecision(@RequestBody(required = false) Object body) {
        return forward(() -> rest.postForObject(policyUrl + "/api/governance/decisions",
                                                body == null ? Map.of() : body, Object.class),
                       Map.of("live", false, "realDataOnly", true, "error", "policy-governance unavailable"));
    }

    @GetMapping({"/governance/decisions", "/policies/evaluations"})
    public ResponseEntity<Object> governanceDecisions(@RequestParam(defaultValue = "100") int limit,
                                                      @RequestParam(required = false) String outcome,
                                                      @RequestParam(required = false) String correlationId) {
        StringBuilder url = new StringBuilder(policyUrl + "/api/governance/decisions?limit=" + limit);
        if (outcome != null) url.append("&outcome=").append(enc(outcome));
        if (correlationId != null) url.append("&correlationId=").append(enc(correlationId));
        return forward(() -> rest.getForObject(url.toString(), Object.class),
                       Map.of("live", false, "realDataOnly", true, "items", List.of(), "error", "policy-governance unavailable"));
    }

    @GetMapping("/governance/approvals")
    public ResponseEntity<Object> governanceApprovals(@RequestParam(defaultValue = "100") int limit,
                                                      @RequestParam(required = false) String status) {
        StringBuilder url = new StringBuilder(policyUrl + "/api/governance/approvals?limit=" + limit);
        if (status != null) url.append("&status=").append(enc(status));
        return forward(() -> rest.getForObject(url.toString(), Object.class),
                       Map.of("live", false, "realDataOnly", true, "items", List.of(), "error", "policy-governance unavailable"));
    }

    @PostMapping("/governance/approvals/{id}/approve")
    public ResponseEntity<Object> approveGovernanceRequest(@PathVariable String id, @RequestBody(required = false) Object body) {
        return forward(() -> rest.postForObject(policyUrl + "/api/governance/approvals/" + id + "/approve",
                                                body == null ? Map.of() : body, Object.class),
                       Map.of("live", false, "realDataOnly", true, "error", "policy-governance unavailable"));
    }

    @PostMapping("/governance/approvals/{id}/reject")
    public ResponseEntity<Object> rejectGovernanceRequest(@PathVariable String id, @RequestBody(required = false) Object body) {
        return forward(() -> rest.postForObject(policyUrl + "/api/governance/approvals/" + id + "/reject",
                                                body == null ? Map.of() : body, Object.class),
                       Map.of("live", false, "realDataOnly", true, "error", "policy-governance unavailable"));
    }

    @GetMapping("/governance/violations")
    public ResponseEntity<Object> governanceViolations(@RequestParam(defaultValue = "100") int limit) {
        return forward(() -> rest.getForObject(policyUrl + "/api/governance/violations?limit=" + limit, Object.class),
                       Map.of("live", false, "realDataOnly", true, "items", List.of(), "error", "policy-governance unavailable"));
    }



    // ── Progressive Delivery Center proxy ───────────────────────────────────

    @GetMapping({"/progressive-delivery", "/progressive-delivery/overview"})
    public ResponseEntity<Object> progressiveOverview(@RequestParam(defaultValue = "all") String namespace) {
        return forward(() -> rest.getForObject(progressiveDeliveryUrl + "/api/progressive-delivery/overview?namespace=" + enc(namespace), Object.class),
                       Map.of("live", false, "realDataOnly", true, "items", List.of(), "error", "progressive-delivery-service unavailable"));
    }

    @GetMapping("/progressive-delivery/rollouts")
    public ResponseEntity<Object> progressiveRollouts(@RequestParam(defaultValue = "all") String namespace) {
        return forward(() -> rest.getForObject(progressiveDeliveryUrl + "/api/progressive-delivery/rollouts?namespace=" + enc(namespace), Object.class),
                       Map.of("live", false, "realDataOnly", true, "items", List.of(), "error", "progressive-delivery-service unavailable"));
    }

    @GetMapping("/progressive-delivery/analysis-runs")
    public ResponseEntity<Object> progressiveAnalysisRuns(@RequestParam(defaultValue = "all") String namespace) {
        return forward(() -> rest.getForObject(progressiveDeliveryUrl + "/api/progressive-delivery/analysis-runs?namespace=" + enc(namespace), Object.class),
                       Map.of("live", false, "realDataOnly", true, "items", List.of(), "error", "progressive-delivery-service unavailable"));
    }

    @GetMapping("/progressive-delivery/experiments")
    public ResponseEntity<Object> progressiveExperiments(@RequestParam(defaultValue = "all") String namespace) {
        return forward(() -> rest.getForObject(progressiveDeliveryUrl + "/api/progressive-delivery/experiments?namespace=" + enc(namespace), Object.class),
                       Map.of("live", false, "realDataOnly", true, "items", List.of(), "error", "progressive-delivery-service unavailable"));
    }

    @GetMapping("/progressive-delivery/applications")
    public ResponseEntity<Object> progressiveApplications() {
        return forward(() -> rest.getForObject(progressiveDeliveryUrl + "/api/progressive-delivery/applications", Object.class),
                       Map.of("live", false, "realDataOnly", true, "items", List.of(), "error", "progressive-delivery-service unavailable"));
    }

    @PostMapping("/progressive-delivery/applications/{app}/sync")
    public ResponseEntity<Object> progressiveSyncApplication(@PathVariable String app) {
        return forward(() -> rest.postForObject(progressiveDeliveryUrl + "/api/progressive-delivery/applications/" + enc(app) + "/sync", Map.of(), Object.class),
                       Map.of("live", false, "realDataOnly", true, "error", "progressive-delivery-service unavailable"));
    }

    @PostMapping("/progressive-delivery/rollouts/{namespace}/{name}/promote")
    public ResponseEntity<Object> progressivePromote(@PathVariable String namespace, @PathVariable String name,
                                                     @RequestParam(defaultValue = "false") boolean full) {
        return forward(() -> rest.postForObject(progressiveDeliveryUrl + "/api/progressive-delivery/rollouts/" + enc(namespace) + "/" + enc(name) + "/promote?full=" + full, Map.of(), Object.class),
                       Map.of("live", false, "realDataOnly", true, "error", "progressive-delivery-service unavailable"));
    }

    @PostMapping("/progressive-delivery/rollouts/{namespace}/{name}/abort")
    public ResponseEntity<Object> progressiveAbort(@PathVariable String namespace, @PathVariable String name) {
        return forward(() -> rest.postForObject(progressiveDeliveryUrl + "/api/progressive-delivery/rollouts/" + enc(namespace) + "/" + enc(name) + "/abort", Map.of(), Object.class),
                       Map.of("live", false, "realDataOnly", true, "error", "progressive-delivery-service unavailable"));
    }

    @PostMapping("/progressive-delivery/rollouts/{namespace}/{name}/restart")
    public ResponseEntity<Object> progressiveRestart(@PathVariable String namespace, @PathVariable String name) {
        return forward(() -> rest.postForObject(progressiveDeliveryUrl + "/api/progressive-delivery/rollouts/" + enc(namespace) + "/" + enc(name) + "/restart", Map.of(), Object.class),
                       Map.of("live", false, "realDataOnly", true, "error", "progressive-delivery-service unavailable"));
    }

    @GetMapping("/progressive-delivery/rollouts/{namespace}/{name}/history")
    public ResponseEntity<Object> progressiveHistory(@PathVariable String namespace, @PathVariable String name) {
        return forward(() -> rest.getForObject(progressiveDeliveryUrl + "/api/progressive-delivery/rollouts/" + enc(namespace) + "/" + enc(name) + "/history", Object.class),
                       Map.of("live", false, "realDataOnly", true, "error", "progressive-delivery-service unavailable"));
    }

    // ── Platform events proxy ─────────────────────────────────────────────────

    @GetMapping("/events")
    public ResponseEntity<Object> platformEvents(@RequestParam(defaultValue = "100") int limit,
                                                 @RequestParam(required = false) String correlationId) {
        String url = auditUrl + "/api/events?limit=" + limit + (correlationId != null ? "&correlationId=" + enc(correlationId) : "");
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

    private HttpEntity<Object> jsonEntity(Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body == null ? Map.of() : body, h);
    }

    private String resolveOrganization(String organizationId) {
        return organizationId == null || organizationId.isBlank()
                ? (defaultOrganizationId == null || defaultOrganizationId.isBlank() ? "nebulaops" : defaultOrganizationId.trim())
                : organizationId.trim();
    }

    private String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

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
