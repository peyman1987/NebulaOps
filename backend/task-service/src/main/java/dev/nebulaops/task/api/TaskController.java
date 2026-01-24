package dev.nebulaops.task.api;

import dev.nebulaops.task.domain.*;
import dev.nebulaops.task.repo.TaskRepository;

import java.time.Instant;
import java.util.*;

import dev.nebulaops.task.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskRepository repo;
    private final RabbitTemplate rabbit;

    public TaskController(TaskRepository repo, RabbitTemplate rabbit) {
        this.repo = repo;
        this.rabbit = rabbit;
    }

    @GetMapping
    public List<TaskDocument> list(@RequestParam(defaultValue = "default-org") String organizationId) {
        return repo.findByOrganizationIdOrderByStatusAscSortOrderAscUpdatedAtDesc(organizationId);
    }

    @PostMapping
    public TaskDocument create(@RequestBody CreateTaskRequest r) {
        if (r.title() == null || r.title().isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        var now = Instant.now();
        var organizationId = r.organizationId() == null || r.organizationId().isBlank() ? "default-org" : r.organizationId();
        var status = r.status() == null ? TaskStatus.TODO : r.status();
        var saved = repo.save(new TaskDocument(
                null,
                organizationId,
                r.projectId() == null || r.projectId().isBlank() ? "portfolio" : r.projectId(),
                r.title(),
                r.description(),
                status,
                r.priority() == null ? TaskPriority.MEDIUM : r.priority(),
                normalizeAssignee(r.assigneeId()),
                r.labels() == null ? List.of() : r.labels(),
                r.dueAt(),
                r.sortOrder() == null ? nextSortOrder(organizationId, status) : r.sortOrder(),
                now,
                now));

        publish("TaskCreated", saved, "Task created and assigned");
        return saved;
    }

    @GetMapping("/{id}")
    public TaskDocument get(@PathVariable String id) {
        return repo.findById(id).orElseThrow();
    }

    @PutMapping("/{id}")
    public TaskDocument update(@PathVariable String id, @RequestBody UpdateTaskRequest r) {
        var existing = repo.findById(id).orElseThrow();
        var status = r.status() == null ? existing.status() : r.status();
        var updated = new TaskDocument(
                existing.id(),
                existing.organizationId(),
                r.projectId() == null ? existing.projectId() : r.projectId(),
                r.title() == null || r.title().isBlank() ? existing.title() : r.title(),
                r.description() == null ? existing.description() : r.description(),
                status,
                r.priority() == null ? existing.priority() : r.priority(),
                r.assigneeId() == null ? existing.assigneeId() : normalizeAssignee(r.assigneeId()),
                r.labels() == null ? existing.labels() : r.labels(),
                r.dueAt() == null ? existing.dueAt() : r.dueAt(),
                r.sortOrder() == null ? existing.sortOrder() : r.sortOrder(),
                existing.createdAt(),
                Instant.now());
        var saved = repo.save(updated);
        publish("TaskUpdated", saved, "Task updated");
        return saved;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        var existing = repo.findById(id).orElseThrow();
        repo.deleteById(id);
        publish("TaskDeleted", existing, "Task removed");
        return Map.of("id", id, "deleted", true);
    }

    @PatchMapping("/{id}/status/{status}")
    public TaskDocument status(@PathVariable String id, @PathVariable TaskStatus status) {
        var t = repo.findById(id).orElseThrow();
        var updated = new TaskDocument(
                t.id(), t.organizationId(), t.projectId(), t.title(), t.description(), status, t.priority(),
                t.assigneeId(), t.labels(), t.dueAt(), t.sortOrder(), t.createdAt(), Instant.now());
        var saved = repo.save(updated);
        publish("TaskStatusChanged", saved, "Task moved to " + status);
        return saved;
    }

    @PatchMapping("/{id}/move")
    public TaskDocument move(@PathVariable String id, @RequestBody MoveTaskRequest r) {
        var t = repo.findById(id).orElseThrow();
        var targetStatus = r.status() == null ? t.status() : r.status();
        var updated = new TaskDocument(
                t.id(), t.organizationId(), t.projectId(), t.title(), t.description(), targetStatus, t.priority(),
                t.assigneeId(), t.labels(), t.dueAt(), r.sortOrder() == null ? t.sortOrder() : r.sortOrder(),
                t.createdAt(), Instant.now());
        var saved = repo.save(updated);
        publish("TaskMoved", saved, "Task moved to " + targetStatus);
        return saved;
    }

    private Integer nextSortOrder(String organizationId, TaskStatus status) {
        return repo.findByOrganizationIdOrderByStatusAscSortOrderAscUpdatedAtDesc(organizationId).stream()
                .filter(task -> task.status() == status)
                .map(TaskDocument::sortOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(value -> value + 100)
                .orElse(100);
    }

    private String normalizeAssignee(String assigneeId) {
        return assigneeId == null || assigneeId.isBlank() ? "unassigned" : assigneeId.trim();
    }

    private void publish(String type, TaskDocument task, String action) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("type", type);
        event.put("taskId", task.id());
        event.put("organizationId", task.organizationId());
        event.put("title", task.title());
        event.put("status", task.status().name());
        event.put("assigneeId", task.assigneeId());
        event.put("recipientUserId", task.assigneeId());
        event.put("notificationMessage", action + ": " + task.title()
                + (task.assigneeId() == null || task.assigneeId().isBlank() || "unassigned".equals(task.assigneeId())
                ? "" : " · assigned to " + task.assigneeId()));
        event.put("occurredAt", Instant.now().toString());
        rabbit.convertAndSend(RabbitMqConfig.TASK_EXCHANGE, RabbitMqConfig.TASK_ROUTING_KEY, event);
    }

    public record CreateTaskRequest(
            String organizationId,
            String projectId,
            String title,
            String description,
            TaskStatus status,
            TaskPriority priority,
            String assigneeId,
            List<String> labels,
            Instant dueAt,
            Integer sortOrder) {
    }

    public record UpdateTaskRequest(
            String projectId,
            String title,
            String description,
            TaskStatus status,
            TaskPriority priority,
            String assigneeId,
            List<String> labels,
            Instant dueAt,
            Integer sortOrder) {
    }

    public record MoveTaskRequest(TaskStatus status, Integer sortOrder) {
    }
}
