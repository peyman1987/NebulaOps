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
    public List<TaskDocument> list(@RequestParam(defaultValue = "demo-org") String organizationId) {
        return repo.findByOrganizationId(organizationId);
    }

    @PostMapping
    public TaskDocument create(@RequestBody CreateTaskRequest r) {
        if (r.title() == null || r.title().isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        var now = Instant.now();
        var organizationId = r.organizationId() == null || r.organizationId().isBlank() ? "demo-org" : r.organizationId();
        var saved = repo.save(new TaskDocument(
                null,
                organizationId,
                r.projectId() == null ? "portfolio" : r.projectId(),
                r.title(),
                r.description(),
                TaskStatus.TODO,
                r.priority() == null ? TaskPriority.MEDIUM : r.priority(),
                r.assigneeId(),
                r.labels() == null ? List.of() : r.labels(),
                r.dueAt(),
                now,
                now));

        publish("TaskCreated", saved);
        return saved;
    }


    @GetMapping("/{id}")
    public TaskDocument get(@PathVariable String id) {
        return repo.findById(id).orElseThrow();
    }

    @PutMapping("/{id}")
    public TaskDocument update(@PathVariable String id, @RequestBody UpdateTaskRequest r) {
        var existing = repo.findById(id).orElseThrow();
        var updated = new TaskDocument(
                existing.id(),
                existing.organizationId(),
                r.projectId() == null ? existing.projectId() : r.projectId(),
                r.title() == null || r.title().isBlank() ? existing.title() : r.title(),
                r.description() == null ? existing.description() : r.description(),
                r.status() == null ? existing.status() : r.status(),
                r.priority() == null ? existing.priority() : r.priority(),
                r.assigneeId() == null ? existing.assigneeId() : r.assigneeId(),
                r.labels() == null ? existing.labels() : r.labels(),
                r.dueAt() == null ? existing.dueAt() : r.dueAt(),
                existing.createdAt(),
                Instant.now());
        var saved = repo.save(updated);
        publish("TaskUpdated", saved);
        return saved;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        var existing = repo.findById(id).orElseThrow();
        repo.deleteById(id);
        publish("TaskDeleted", existing);
        return Map.of("id", id, "deleted", true);
    }

    @PatchMapping("/{id}/status/{status}")
    public TaskDocument status(@PathVariable String id, @PathVariable TaskStatus status) {
        var t = repo.findById(id).orElseThrow();
        var updated = new TaskDocument(
                t.id(), t.organizationId(), t.projectId(), t.title(), t.description(), status, t.priority(),
                t.assigneeId(), t.labels(), t.dueAt(), t.createdAt(), Instant.now());
        var saved = repo.save(updated);
        publish("TaskStatusChanged", saved);
        return saved;
    }

    private void publish(String type, TaskDocument task) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("type", type);
        event.put("taskId", task.id());
        event.put("organizationId", task.organizationId());
        event.put("occurredAt", Instant.now().toString());
        rabbit.convertAndSend(RabbitMqConfig.TASK_EXCHANGE, RabbitMqConfig.TASK_ROUTING_KEY, event);
    }

    public record CreateTaskRequest(
            String organizationId,
            String projectId,
            String title,
            String description,
            TaskPriority priority,
            String assigneeId,
            List<String> labels,
            Instant dueAt) {
    }

    public record UpdateTaskRequest(
            String projectId,
            String title,
            String description,
            TaskStatus status,
            TaskPriority priority,
            String assigneeId,
            List<String> labels,
            Instant dueAt) {
    }
}
