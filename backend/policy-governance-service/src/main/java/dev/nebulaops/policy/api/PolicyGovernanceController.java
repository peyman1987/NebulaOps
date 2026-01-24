package dev.nebulaops.policy.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping({"/api/governance", "/api/policies"})
@SuppressWarnings({"unchecked", "rawtypes"})
public class PolicyGovernanceController {
    private static final String DECISIONS = "governance_decisions";
    private static final String APPROVALS = "governance_approvals";
    private static final String POLICY_METADATA = "governance_policy_metadata";

    private final MongoTemplate mongo;
    private final RestTemplate rest = new RestTemplate();
    private final String opaUrl;
    private final String auditUrl;
    private final String notificationUrl;
    private final String enforcementMode;

    public PolicyGovernanceController(
            MongoTemplate mongo,
            @Value("${nebulaops.opa.url:http://opa:8181}") String opaUrl,
            @Value("${nebulaops.audit.url:http://audit-service:8101}") String auditUrl,
            @Value("${nebulaops.notification.url:http://notification-service:8083}") String notificationUrl,
            @Value("${nebulaops.governance.enforcement-mode:monitor}") String enforcementMode) {
        this.mongo = mongo;
        this.opaUrl = trimSlash(opaUrl);
        this.auditUrl = trimSlash(auditUrl);
        this.notificationUrl = trimSlash(notificationUrl);
        this.enforcementMode = enforcementMode == null || enforcementMode.isBlank() ? "monitor" : enforcementMode;
    }

    @GetMapping({"", "/summary"})
    public ResponseEntity<Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", true);
        out.put("realDataOnly", true);
        out.put("source", "policy-governance-service");
        out.put("enforcementMode", enforcementMode);
        out.put("policyEngine", opaHealth());
        out.put("decisions", count(DECISIONS, new Query()));
        out.put("approvalsPending", count(APPROVALS, new Query(Criteria.where("status").is("PENDING"))));
        out.put("violations", count(DECISIONS, new Query(Criteria.where("outcome").is("DENY"))));
        out.put("generatedAt", Instant.now().toString());
        out.put("toolStatus", "Governance summary is calculated from runtime MongoDB collections and the live OPA policy engine.");
        return ResponseEntity.ok(out);
    }

    @GetMapping("/policies")
    public ResponseEntity<Object> policies() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", true);
        out.put("realDataOnly", true);
        out.put("source", "opa");
        out.put("opaUrl", opaUrl);
        out.put("fetchedAt", Instant.now().toString());

        Map<String, Object> opa = get(opaUrl + "/v1/policies", Map.of());
        if (Boolean.FALSE.equals(opa.get("available"))) {
            out.put("live", false);
            out.put("items", List.of());
            out.put("error", opa.get("error"));
            out.put("toolStatus", "OPA is unavailable. No policy records are fabricated.");
            return ResponseEntity.ok(out);
        }

        List<Map<String, Object>> items = new ArrayList<>();
        Object result = opa.get("result");
        if (result instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> policy = new LinkedHashMap<>();
                    policy.put("id", String.valueOf(m.containsKey("id") ? m.get("id") : ""));
                    policy.put("raw", m);
                    policy.put("source", "OPA /v1/policies");
                    policy.put("status", "LOADED");
                    items.add(policy);
                }
            }
        }
        items.addAll(find(POLICY_METADATA, new Query(), 100));
        out.put("count", items.size());
        out.put("items", items);
        out.put("toolStatus", items.isEmpty()
                ? "OPA returned no loaded policies. Mount infrastructure/opa/policies into the OPA container."
                : "Policies loaded from the live OPA policy API.");
        return ResponseEntity.ok(out);
    }

    @PostMapping("/policies")
    public ResponseEntity<Object> upsertPolicy(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> input = body == null ? Map.of() : body;
        String id = safeId(String.valueOf(input.getOrDefault("id", "runtime-policy-" + UUID.randomUUID())));
        Object rego = input.get("rego");
        if (rego == null || String.valueOf(rego).isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "live", true,
                    "realDataOnly", true,
                    "error", "Missing rego field. Policies must be supplied as real OPA/Rego content, not generated placeholder records."));
        }

        Map<String, Object> opa = putText(opaUrl + "/v1/policies/" + id, String.valueOf(rego));
        if (Boolean.FALSE.equals(opa.get("available"))) {
            return ResponseEntity.ok(Map.of(
                    "live", false,
                    "realDataOnly", true,
                    "id", id,
                    "stored", false,
                    "error", opa.get("error"),
                    "toolStatus", "OPA policy upload failed. The service did not create a fake policy."));
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("id", id);
        metadata.put("name", input.getOrDefault("name", id));
        metadata.put("source", "OPA /v1/policies/" + id);
        metadata.put("status", "LOADED");
        metadata.put("updatedAt", Instant.now().toString());
        metadata.put("realDataOnly", true);
        upsert(POLICY_METADATA, id, metadata);
        publish("POLICY_UPSERTED", "INFO", "corr-" + UUID.randomUUID(), metadata, actor(input));
        return ResponseEntity.ok(metadata);
    }

    @PutMapping("/policies/{id}")
    public ResponseEntity<Object> updatePolicy(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Map<String, Object> merged = new LinkedHashMap<>(body == null ? Map.of() : body);
        merged.put("id", id);
        return upsertPolicy(merged);
    }

    @DeleteMapping("/policies/{id}")
    public ResponseEntity<Object> deletePolicy(@PathVariable String id) {
        Map<String, Object> opa = delete(opaUrl + "/v1/policies/" + safeId(id));
        Query q = new Query(Criteria.where("id").is(id));
        mongo.remove(q, POLICY_METADATA);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", !Boolean.FALSE.equals(opa.get("available")));
        out.put("realDataOnly", true);
        out.put("id", id);
        out.put("deleted", !Boolean.FALSE.equals(opa.get("available")));
        if (Boolean.FALSE.equals(opa.get("available"))) out.put("error", opa.get("error"));
        publish("POLICY_DELETED", "WARN", "corr-" + UUID.randomUUID(), out, "operator");
        return ResponseEntity.ok(out);
    }

    @PostMapping({"/decisions", "/evaluate"})
    public ResponseEntity<Object> decide(@RequestBody(required = false) Map<String, Object> body,
                                         @RequestHeader(value = "X-Correlation-Id", required = false) String headerCorrelationId) {
        Map<String, Object> input = normalizeDecisionInput(body == null ? Map.of() : body);
        String id = "dec-" + UUID.randomUUID();
        String correlationId = headerCorrelationId == null || headerCorrelationId.isBlank()
                ? String.valueOf(input.getOrDefault("correlationId", "corr-" + UUID.randomUUID()))
                : headerCorrelationId;
        input.put("correlationId", correlationId);
        input.putIfAbsent("requestedAt", Instant.now().toString());

        Map<String, Object> opaResponse = post(opaUrl + "/v1/data/nebulaops/governance/decision", Map.of("input", input), Map.of());
        if (Boolean.FALSE.equals(opaResponse.get("available"))) {
            Map<String, Object> unavailable = new LinkedHashMap<>();
            unavailable.put("id", id);
            unavailable.put("live", false);
            unavailable.put("realDataOnly", true);
            unavailable.put("source", "policy-governance-service");
            unavailable.put("policyEngine", "UNAVAILABLE");
            unavailable.put("outcome", "POLICY_ENGINE_UNAVAILABLE");
            unavailable.put("allow", false);
            unavailable.put("approvalRequired", false);
            unavailable.put("enforcementMode", enforcementMode);
            unavailable.put("input", input);
            unavailable.put("error", opaResponse.get("error"));
            unavailable.put("correlationId", correlationId);
            unavailable.put("evaluatedAt", Instant.now().toString());
            mongo.save(unavailable, DECISIONS);
            publish("GOVERNANCE_DECISION_UNAVAILABLE", "ERROR", correlationId, unavailable, actor(input));
            return ResponseEntity.ok(unavailable);
        }

        Map<String, Object> result = asMap(opaResponse.get("result"));
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("id", id);
        decision.put("live", true);
        decision.put("realDataOnly", true);
        decision.put("source", "OPA");
        decision.put("policyEngine", opaUrl);
        decision.put("enforcementMode", enforcementMode);
        decision.put("allow", bool(result.get("allow")));
        decision.put("approvalRequired", bool(result.get("approvalRequired")));
        decision.put("outcome", String.valueOf(result.getOrDefault("outcome", bool(result.get("allow")) ? "ALLOW" : "DENY")));
        decision.put("severity", String.valueOf(result.getOrDefault("severity", "INFO")));
        decision.put("reasons", result.getOrDefault("reasons", List.of()));
        decision.put("approvalReason", result.getOrDefault("approvalReason", ""));
        decision.put("policyPackage", result.getOrDefault("policyPackage", "nebulaops.governance"));
        decision.put("policyVersion", result.getOrDefault("policyVersion", "unknown"));
        decision.put("input", input);
        decision.put("correlationId", correlationId);
        decision.put("evaluatedAt", Instant.now().toString());

        mongo.save(decision, DECISIONS);
        if (bool(decision.get("approvalRequired")) && !bool(decision.get("allow"))) {
            Map<String, Object> approval = createApproval(decision, input);
            decision.put("approvalId", approval.get("id"));
        }

        String type = "GOVERNANCE_" + String.valueOf(decision.get("outcome"));
        String severity = String.valueOf(decision.get("severity"));
        publish(type, severity, correlationId, decision, actor(input));
        return ResponseEntity.ok(decision);
    }

    @GetMapping({"/decisions", "/evaluations"})
    public ResponseEntity<Object> decisions(@RequestParam(defaultValue = "100") int limit,
                                            @RequestParam(required = false) String outcome,
                                            @RequestParam(required = false) String correlationId) {
        Query q = new Query().with(Sort.by(Sort.Direction.DESC, "evaluatedAt")).limit(bound(limit));
        List<Criteria> criteria = new ArrayList<>();
        if (outcome != null && !outcome.isBlank()) criteria.add(Criteria.where("outcome").is(outcome));
        if (correlationId != null && !correlationId.isBlank()) criteria.add(Criteria.where("correlationId").is(correlationId));
        if (!criteria.isEmpty()) q.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        return listResponse("governance_decisions", DECISIONS, q,
                "Governance decisions are persisted only after real runtime requests reach POST /api/governance/decisions.");
    }

    @GetMapping("/violations")
    public ResponseEntity<Object> violations(@RequestParam(defaultValue = "100") int limit) {
        Query q = new Query(Criteria.where("outcome").is("DENY"))
                .with(Sort.by(Sort.Direction.DESC, "evaluatedAt"))
                .limit(bound(limit));
        return listResponse("governance_violations", DECISIONS, q,
                "No runtime policy violations have been recorded yet.");
    }

    @GetMapping("/approvals")
    public ResponseEntity<Object> approvals(@RequestParam(defaultValue = "100") int limit,
                                            @RequestParam(required = false) String status) {
        Query q = new Query().with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(bound(limit));
        if (status != null && !status.isBlank()) q.addCriteria(Criteria.where("status").is(status));
        return listResponse("governance_approvals", APPROVALS, q,
                "No approval requests exist yet. Requests are created only by real policy decisions requiring approval.");
    }

    @PostMapping("/approvals/{id}/approve")
    public ResponseEntity<Object> approve(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        return updateApproval(id, "APPROVED", body == null ? Map.of() : body);
    }

    @PostMapping("/approvals/{id}/reject")
    public ResponseEntity<Object> reject(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        return updateApproval(id, "REJECTED", body == null ? Map.of() : body);
    }

    private ResponseEntity<Object> updateApproval(String id, String status, Map<String, Object> body) {
        Query q = new Query(Criteria.where("id").is(id));
        Map<String, Object> existing = mongo.findOne(q, Map.class, APPROVALS);
        if (existing == null) {
            return ResponseEntity.ok(Map.of("live", true, "realDataOnly", true, "id", id, "status", "NOT_FOUND"));
        }
        String correlationId = String.valueOf(existing.getOrDefault("correlationId", "corr-" + UUID.randomUUID()));
        Update update = new Update()
                .set("status", status)
                .set("reviewedAt", Instant.now().toString())
                .set("reviewedBy", actor(body))
                .set("reviewComment", body.getOrDefault("comment", ""));
        mongo.updateFirst(q, update, APPROVALS);
        Map<String, Object> updated = mongo.findOne(q, Map.class, APPROVALS);
        publish("GOVERNANCE_APPROVAL_" + status, "APPROVED".equals(status) ? "INFO" : "WARN", correlationId, updated, actor(body));
        return ResponseEntity.ok(updated);
    }

    private Map<String, Object> createApproval(Map<String, Object> decision, Map<String, Object> input) {
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("id", "apr-" + UUID.randomUUID());
        approval.put("decisionId", decision.get("id"));
        approval.put("correlationId", decision.get("correlationId"));
        approval.put("status", "PENDING");
        approval.put("action", input.getOrDefault("action", "unknown"));
        approval.put("target", input.getOrDefault("target", Map.of()));
        approval.put("requestedBy", actor(input));
        approval.put("createdAt", Instant.now().toString());
        approval.put("reason", decision.getOrDefault("approvalReason", "Policy requires approval"));
        approval.put("decision", decision);
        approval.put("realDataOnly", true);
        mongo.save(approval, APPROVALS);
        publish("GOVERNANCE_APPROVAL_REQUESTED", "WARN", String.valueOf(approval.get("correlationId")), approval, actor(input));
        return approval;
    }

    private ResponseEntity<Object> listResponse(String source, String collection, Query query, String emptyStatus) {
        List<Map<String, Object>> items = find(collection, query, query.getLimit() > 0 ? query.getLimit() : 100);
        return ResponseEntity.ok(Map.of(
                "live", true,
                "realDataOnly", true,
                "source", source,
                "count", items.size(),
                "items", items,
                "toolStatus", items.isEmpty() ? emptyStatus : "Runtime records returned from MongoDB collection " + collection
        ));
    }

    private Map<String, Object> normalizeDecisionInput(Map<String, Object> body) {
        Map<String, Object> input = new LinkedHashMap<>(body);
        if (!input.containsKey("action")) {
            input.put("action", body.containsKey("release") ? "release.promote" : "runtime.evaluate");
        }
        if (!input.containsKey("actor")) {
            input.put("actor", Map.of("username", body.getOrDefault("actor", "operator"), "roles", List.of("nebula-operator")));
        }
        if (!input.containsKey("target")) {
            Object target = body.getOrDefault("target", body.getOrDefault("application", "current-runtime"));
            input.put("target", Map.of("name", target));
        }
        if (!input.containsKey("payload")) {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (body.containsKey("release")) payload.put("release", body.get("release"));
            if (body.containsKey("image")) payload.put("image", body.get("image"));
            input.put("payload", payload);
        }
        return input;
    }

    private void publish(String type, String severity, String correlationId, Map<String, Object> payload, String actor) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("source", "policy-governance-service");
        event.put("actor", actor == null || actor.isBlank() ? "operator" : actor);
        event.put("severity", severity == null || severity.isBlank() ? "INFO" : severity);
        event.put("correlationId", correlationId);
        event.put("timestamp", Instant.now().toString());
        event.put("payload", payload == null ? Map.of() : payload);
        post(auditUrl + "/api/audit/events", event, Map.of());

        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("type", type);
        notification.put("source", "policy-governance-service");
        notification.put("severity", event.get("severity"));
        notification.put("message", governanceMessage(type, payload));
        notification.put("correlationId", correlationId);
        notification.put("payload", payload == null ? Map.of() : payload);
        post(notificationUrl + "/api/notifications", notification, Map.of());
    }

    private String governanceMessage(String type, Map<String, Object> payload) {
        String outcome = payload == null ? "" : String.valueOf(payload.getOrDefault("outcome", ""));
        String approval = payload == null ? "" : String.valueOf(payload.getOrDefault("id", ""));
        if (type.contains("APPROVAL_REQUESTED")) return "Governance approval requested: " + approval;
        if (type.contains("DENY")) return "Governance policy denied a runtime action.";
        if (type.contains("ALLOW")) return "Governance policy allowed a runtime action.";
        if (!outcome.isBlank()) return "Governance decision: " + outcome;
        return type;
    }

    private Map<String, Object> opaHealth() {
        Map<String, Object> health = get(opaUrl + "/health", Map.of());
        if (Boolean.FALSE.equals(health.get("available"))) {
            return Map.of("available", false, "url", opaUrl, "error", health.get("error"));
        }
        return Map.of("available", true, "url", opaUrl, "raw", health);
    }

    private Map<String, Object> get(String url, Map<String, Object> unavailablePayload) {
        try {
            Object body = rest.getForObject(url, Object.class);
            if (body instanceof Map) return (Map<String, Object>) body;
            return Map.of("available", true, "result", body == null ? Map.of() : body);
        } catch (Exception e) {
            Map<String, Object> m = new LinkedHashMap<>(unavailablePayload);
            m.put("available", false);
            m.put("error", e.getMessage());
            return m;
        }
    }

    private Map<String, Object> post(String url, Object payload, Map<String, Object> unavailablePayload) {
        try {
            Object body = rest.postForObject(url, payload, Object.class);
            if (body instanceof Map) return (Map<String, Object>) body;
            return Map.of("available", true, "result", body == null ? Map.of() : body);
        } catch (Exception e) {
            Map<String, Object> m = new LinkedHashMap<>(unavailablePayload);
            m.put("available", false);
            m.put("error", e.getMessage());
            return m;
        }
    }

    private Map<String, Object> putText(String url, String rego) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            Object body = rest.exchange(url, HttpMethod.PUT, new HttpEntity<>(rego, headers), Object.class).getBody();
            return body instanceof Map ? (Map<String, Object>) body : Map.of("available", true, "result", body == null ? Map.of() : body);
        } catch (Exception e) {
            return Map.of("available", false, "error", e.getMessage());
        }
    }

    private Map<String, Object> delete(String url) {
        try {
            rest.delete(url);
            return Map.of("available", true);
        } catch (Exception e) {
            return Map.of("available", false, "error", e.getMessage());
        }
    }

    private void upsert(String collection, String id, Map<String, Object> doc) {
        Query q = new Query(Criteria.where("id").is(id));
        Map<String, Object> existing = mongo.findOne(q, Map.class, collection);
        if (existing == null) mongo.save(doc, collection);
        else {
            Update u = new Update();
            doc.forEach(u::set);
            mongo.updateFirst(q, u, collection);
        }
    }

    private List<Map<String, Object>> find(String collection, Query q, int limit) {
        if (q.getLimit() == 0) q.limit(bound(limit));
        List<Map> raw = mongo.find(q, Map.class, collection);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map item : raw) out.add(new LinkedHashMap<>(item));
        return out;
    }

    private long count(String collection, Query q) {
        try { return mongo.count(q, collection); } catch (Exception e) { return 0; }
    }

    private int bound(int n) { return Math.max(1, Math.min(n, 1000)); }
    private String trimSlash(String s) { return s == null ? "" : s.replaceAll("/+$", ""); }
    private Map<String, Object> asMap(Object o) { return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>(); }
    private boolean bool(Object o) { return o instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(o)); }
    private String actor(Map<String, Object> input) {
        Object actor = input.get("actor");
        if (actor instanceof Map<?, ?> m) return String.valueOf(m.containsKey("username") ? m.get("username") : "operator");
        return actor == null ? "operator" : String.valueOf(actor);
    }
    private String safeId(String value) { return value == null ? "policy" : value.replaceAll("[^A-Za-z0-9_.-]", "-"); }
}
