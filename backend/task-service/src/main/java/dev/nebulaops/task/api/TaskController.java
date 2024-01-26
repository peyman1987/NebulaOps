package dev.nebulaops.task.api;

import dev.nebulaops.task.domain.*;
import dev.nebulaops.task.events.TaskEvent;
import dev.nebulaops.task.repo.TaskRepository;

import java.time.Instant;
import java.util.*;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskRepository repo;
    private final KafkaTemplate<String, Object> kafka;

    public TaskController(TaskRepository repo, KafkaTemplate<String, Object> kafka) {
        this.repo = repo;
        this.kafka = kafka;
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
        kafka.send("nebula.task.events", task.id(), new TaskEvent(
                UUID.randomUUID().toString(), type, task.id(), task.organizationId(), Instant.now()));
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
}
