package dev.nebulaops.gateway.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationGatewayController {

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of("status", "connected", "source", "gateway-service", "timestamp", Instant.now().toString())));
            emitter.send(SseEmitter.event()
                .name("notification")
                .data(Map.of(
                    "id", "ntf-" + UUID.randomUUID(),
                    "type", "GATEWAY_STREAM_READY",
                    "message", "Gateway notification stream is available",
                    "severity", "INFO",
                    "source", "gateway-service",
                    "read", false,
                    "createdAt", Instant.now().toString()
                )));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
        return emitter;
    }
}
