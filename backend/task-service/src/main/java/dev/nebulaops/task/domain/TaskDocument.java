package dev.nebulaops.task.domain;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("tasks")
public record TaskDocument(@Id String id, @Indexed String organizationId, String projectId, String title,
                           String description, TaskStatus status, TaskPriority priority, String assigneeId,
                           List<String> labels, Instant dueAt, Instant createdAt, Instant updatedAt) {
}
