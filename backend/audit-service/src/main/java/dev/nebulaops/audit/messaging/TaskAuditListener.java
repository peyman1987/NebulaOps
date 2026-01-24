package dev.nebulaops.audit.messaging;

import dev.nebulaops.audit.api.AuditEventStore;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TaskAuditListener {
    private final AuditEventStore store;

    public TaskAuditListener(AuditEventStore store) {
        this.store = store;
    }

    @RabbitListener(queues = "${nebulaops.audit.task-events-queue:nebula.audit.task.events}")
    public void receive(Map<String, Object> event) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("type", "TASK_EVENT");
        audit.put("source", "task-service");
        audit.put("actor", event.getOrDefault("actor", event.getOrDefault("updatedBy", "system")));
        audit.put("correlationId", event.getOrDefault("correlationId", event.getOrDefault("traceId", "rabbitmq-task-event")));
        audit.put("severity", "INFO");
        audit.put("payload", event);
        store.record(audit);
    }
}
