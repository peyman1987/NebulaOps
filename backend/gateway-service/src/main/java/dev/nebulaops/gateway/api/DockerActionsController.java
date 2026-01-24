package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.client.DockerSocketClient;
import dev.nebulaops.gateway.service.PlatformEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * v22.4 — Docker container action endpoints.
 *
 * All calls go directly to /var/run/docker.sock via HTTP — no CLI required.
 *
 * POST /api/runtime/docker/containers/{id}/start
 * POST /api/runtime/docker/containers/{id}/stop
 * POST /api/runtime/docker/containers/{id}/restart
 * POST /api/runtime/docker/containers/{id}/pause
 * POST /api/runtime/docker/containers/{id}/unpause
 * DELETE /api/runtime/docker/containers/{id}         (remove container)
 * POST /api/runtime/docker/images/pull               body: {image, tag}
 * POST /api/runtime/docker/images/prune              (remove dangling images)
 * DELETE /api/runtime/docker/images/{id}
 * POST /api/runtime/docker/volumes/prune
 * POST /api/runtime/docker/networks/prune
 */
@RestController
@RequestMapping("/api/runtime/docker")
public class DockerActionsController {

    private static final String SOCKET = "/var/run/docker.sock";

    private final DockerSocketClient socket;
    private final PlatformEventPublisher events;

    public DockerActionsController(DockerSocketClient socket, PlatformEventPublisher events) {
        this.socket = socket;
        this.events = events;
    }

    // ── Container actions ─────────────────────────────────────────────────────

    @PostMapping("/containers/{id}/start")
    public ResponseEntity<Map<String, Object>> startContainer(@PathVariable String id) {
        return action("POST", "/containers/" + id + "/start", null, "start", id);
    }

    @PostMapping("/containers/{id}/stop")
    public ResponseEntity<Map<String, Object>> stopContainer(@PathVariable String id,
            @RequestParam(defaultValue = "10") int t) {
        return action("POST", "/containers/" + id + "/stop?t=" + t, null, "stop", id);
    }

    @PostMapping("/containers/{id}/restart")
    public ResponseEntity<Map<String, Object>> restartContainer(@PathVariable String id,
            @RequestParam(defaultValue = "5") int t) {
        return action("POST", "/containers/" + id + "/restart?t=" + t, null, "restart", id);
    }

    @PostMapping("/containers/{id}/pause")
    public ResponseEntity<Map<String, Object>> pauseContainer(@PathVariable String id) {
        return action("POST", "/containers/" + id + "/pause", null, "pause", id);
    }

    @PostMapping("/containers/{id}/unpause")
    public ResponseEntity<Map<String, Object>> unpauseContainer(@PathVariable String id) {
        return action("POST", "/containers/" + id + "/unpause", null, "unpause", id);
    }

    @DeleteMapping("/containers/{id}")
    public ResponseEntity<Map<String, Object>> removeContainer(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean force,
            @RequestParam(defaultValue = "false") boolean v) {
        return action("DELETE", "/containers/" + id + "?force=" + force + "&v=" + v, null, "remove", id);
    }

    @GetMapping("/containers/{id}/logs")
    public ResponseEntity<Map<String, Object>> containerLogs(@PathVariable String id,
            @RequestParam(defaultValue = "100") int tail,
            @RequestParam(defaultValue = "true") boolean timestamps) {
        try {
            String path = "/containers/" + id + "/logs?stdout=true&stderr=true"
                        + "&tail=" + tail + "&timestamps=" + timestamps;
            String raw = rawGet(path);
            // Docker multiplexed stream — extract text lines
            List<String> lines = parseDockerLogs(raw);
            return ResponseEntity.ok(Map.of("ok", true, "id", id, "logs", lines));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ok", false, "id", id, "logs", List.of(),
                                            "error", e.getMessage()));
        }
    }

    @GetMapping("/containers/{id}/stats")
    public ResponseEntity<Map<String, Object>> containerStats(@PathVariable String id) {
        try {
            String raw = rawGet("/containers/" + id + "/stats?stream=false");
            return ResponseEntity.ok(Map.of("ok", true, "stats", raw));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    // ── Image actions ─────────────────────────────────────────────────────────

    @DeleteMapping("/images/{id}")
    public ResponseEntity<Map<String, Object>> removeImage(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean force) {
        return action("DELETE", "/images/" + id + "?force=" + force, null, "remove-image", id);
    }

    @PostMapping("/images/prune")
    public ResponseEntity<Map<String, Object>> pruneImages() {
        return action("POST", "/images/prune", "{}", "prune-images", "dangling");
    }

    // ── Volume actions ────────────────────────────────────────────────────────

    @DeleteMapping("/volumes/{name}")
    public ResponseEntity<Map<String, Object>> removeVolume(@PathVariable String name) {
        return action("DELETE", "/volumes/" + name, null, "remove-volume", name);
    }

    @PostMapping("/volumes/prune")
    public ResponseEntity<Map<String, Object>> pruneVolumes() {
        return action("POST", "/volumes/prune", "{}", "prune-volumes", "unused");
    }

    // ── Network actions ───────────────────────────────────────────────────────

    @PostMapping("/networks/prune")
    public ResponseEntity<Map<String, Object>> pruneNetworks() {
        return action("POST", "/networks/prune", "{}", "prune-networks", "unused");
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> action(String method, String path,
            String body, String action, String target) {
        try {
            int status = rawRequest(method, path, body);
            boolean ok = status >= 200 && status < 300;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", ok);
            payload.put("action", action);
            payload.put("target", target);
            payload.put("status", status);
            String correlationId = events.mutation("DOCKER_" + action.toUpperCase(Locale.ROOT).replace('-', '_'), target, ok, payload);
            payload.put("correlationId", correlationId);
            return ResponseEntity.ok(payload);
        } catch (Exception e) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", false);
            payload.put("action", action);
            payload.put("target", target);
            payload.put("error", e.getMessage());
            String correlationId = events.mutation("DOCKER_" + action.toUpperCase(Locale.ROOT).replace('-', '_'), target, false, payload);
            payload.put("correlationId", correlationId);
            return ResponseEntity.ok(payload);
        }
    }

    private int rawRequest(String method, String path, String body) throws IOException {
        var address = UnixDomainSocketAddress.of(SOCKET);
        try (var channel = SocketChannel.open(address)) {
            StringBuilder req = new StringBuilder();
            req.append(method).append(' ').append(path).append(" HTTP/1.0\r\n");
            req.append("Host: localhost\r\n");
            if (body != null && !body.isEmpty()) {
                byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                req.append("Content-Type: application/json\r\n");
                req.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
                req.append("\r\n");
                channel.write(ByteBuffer.wrap(req.toString().getBytes(StandardCharsets.UTF_8)));
                channel.write(ByteBuffer.wrap(bodyBytes));
            } else {
                req.append("Content-Length: 0\r\n");
                req.append("\r\n");
                channel.write(ByteBuffer.wrap(req.toString().getBytes(StandardCharsets.UTF_8)));
            }

            var sb = new StringBuilder();
            var buf = ByteBuffer.allocate(8192);
            while (channel.read(buf) != -1) {
                buf.flip();
                sb.append(StandardCharsets.UTF_8.decode(buf));
                buf.clear();
            }
            String response = sb.toString();
            // Parse HTTP status code from first line
            if (response.startsWith("HTTP/")) {
                String firstLine = response.split("\r\n")[0];
                String[] parts = firstLine.split(" ");
                if (parts.length >= 2) return Integer.parseInt(parts[1]);
            }
            return 200;
        }
    }

    private String rawGet(String path) throws IOException {
        var address = UnixDomainSocketAddress.of(SOCKET);
        try (var channel = SocketChannel.open(address)) {
            String req = "GET " + path + " HTTP/1.0\r\nHost: localhost\r\n\r\n";
            channel.write(ByteBuffer.wrap(req.getBytes(StandardCharsets.UTF_8)));
            var sb = new StringBuilder();
            var buf = ByteBuffer.allocate(65536);
            while (channel.read(buf) != -1) {
                buf.flip();
                sb.append(StandardCharsets.UTF_8.decode(buf));
                buf.clear();
            }
            String raw = sb.toString();
            int bodyStart = raw.indexOf("\r\n\r\n");
            return bodyStart >= 0 ? raw.substring(bodyStart + 4).trim() : raw;
        }
    }

    /**
     * Docker multiplexed log stream format:
     *   [stream_type:1][0:3][size:4][payload...]
     * We skip non-printable bytes at line start and strip control chars.
     */
    private List<String> parseDockerLogs(String raw) {
        List<String> lines = new ArrayList<>();
        for (String line : raw.split("\n")) {
            // Strip leading non-printable ASCII (the 8-byte Docker stream header bleeds into UTF-8 string)
            int start = 0;
            while (start < line.length() && line.charAt(start) < 32 && line.charAt(start) != 9) {
                start++;
            }
            String cleaned = line.substring(start).trim();
            if (!cleaned.isEmpty()) {
                lines.add(cleaned);
            }
        }
        return lines;
    }
}
