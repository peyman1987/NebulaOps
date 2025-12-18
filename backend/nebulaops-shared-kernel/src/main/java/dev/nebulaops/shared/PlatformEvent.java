package dev.nebulaops.shared;

import java.time.Instant;
import java.util.Map;

public record PlatformEvent(String id, String type, String severity, String source, String actor, String correlationId, Instant timestamp, Map<String,Object> payload) {}
