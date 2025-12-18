package dev.nebulaops.shared;

import java.time.Instant;
import java.util.Map;

public record AuditEvent(String id, String actor, String action, String target, String outcome, String correlationId, Instant timestamp, Map<String,Object> metadata) {}
