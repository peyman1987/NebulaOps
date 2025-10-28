package dev.nebulaops.mvc.domain;

import java.time.Instant;
import java.util.UUID;

public record WorkItem(
        String id,
        String title,
        String description,
        WorkItemStatus status,
        String owner,
        Instant createdAt,
        Instant updatedAt
) {
    public static WorkItem create(String title, String description, String owner) {
        Instant now = Instant.now();
        return new WorkItem(UUID.randomUUID().toString(), title, description, WorkItemStatus.TODO, owner, now, now);
    }

    public WorkItem withStatus(WorkItemStatus newStatus) {
        return new WorkItem(id, title, description, newStatus, owner, createdAt, Instant.now());
    }

    public WorkItem withContent(String newTitle, String newDescription, String newOwner) {
        return new WorkItem(id, newTitle, newDescription, status, newOwner, createdAt, Instant.now());
    }
}
