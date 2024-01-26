package dev.nebulaops.task.events;

import java.time.Instant;

public record TaskEvent(String eventId, String type, String taskId, String organizationId, Instant occurredAt) {
}
