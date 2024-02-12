package dev.nebulaops.notification.api;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final CopyOnWriteArrayList<Map<String, Object>> inbox = new CopyOnWriteArrayList<>();

    @RabbitListener(queues = "nebula.task.events")
    public void onTaskEvent(Map<String, Object> event) {
        inbox.add(Map.of("type", "TASK_EVENT", "payload", event, "receivedAt", Instant.now().toString()));
    }

    @GetMapping
    public Object list() {
        return inbox;
    }
}
