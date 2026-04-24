package dev.nebulaops.gateway.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.nebulaops.gateway.service.PlatformEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * v23.2 — Docker operational console endpoints.
 *
 * All operations talk to the Docker Engine API through /var/run/docker.sock.
 * No Docker CLI wrapper and no mock/static runtime data are used.
 */
@RestController
@RequestMapping("/api/runtime/docker")
public class DockerActionsController {

    private static final String SOCKET = "/var/run/docker.sock";

    private final PlatformEventPublisher events;
    private final ObjectMapper mapper = new ObjectMapper();

    public DockerActionsController(PlatformEventPublisher events) {
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

    @GetMapping("/containers/{id}/inspect")
    public ResponseEntity<Map<String, Object>> inspectContainer(@PathVariable String id) {
        return inspect("/containers/" + id + "/json", "container", id);
    }

    @GetMapping("/containers/{id}/logs")
    public ResponseEntity<Map<String, Object>> containerLogs(@PathVariable String id,
            @RequestParam(defaultValue = "100") int tail,
            @RequestParam(defaultValue = "true") boolean timestamps) {
        try {
            String path = "/containers/" + id + "/logs?stdout=true&stderr=true"
                        + "&tail=" + Math.max(1, Math.min(tail, 5000))
                        + "&timestamps=" + timestamps;
            String raw = rawGet(path);
            List<String> lines = parseDockerLogs(raw);
            return ResponseEntity.ok(Map.of("ok", true, "live", true, "id", id, "logs", lines, "items", lines));
        } catch (Exception e) {
            return ResponseEntity.ok(errorPayload("container-logs", id, e));
        }
    }

    @GetMapping("/containers/{id}/stats")
    public ResponseEntity<Map<String, Object>> containerStats(@PathVariable String id) {
        try {
            Map<String, Object> stats = jsonMap(rawGet("/containers/" + id + "/stats?stream=false"));
            Map<String, Object> summary = summarizeStats(id, stats);
            return ResponseEntity.ok(Map.of("ok", true, "live", true, "id", id, "stats", stats, "summary", summary, "items", List.of(summary)));
        } catch (Exception e) {
            return ResponseEntity.ok(errorPayload("container-stats", id, e));
        }
    }

    // ── Image actions ─────────────────────────────────────────────────────────

    @PostMapping("/images/pull")
    public ResponseEntity<Map<String, Object>> pullImage(@RequestBody(required = false) Map<String, Object> body) {
        String image = body == null ? "" : String.valueOf(body.getOrDefault("image", ""));
        String tag = body == null ? "latest" : String.valueOf(body.getOrDefault("tag", "latest"));
        if (image == null || image.isBlank()) return ResponseEntity.ok(Map.of("ok", false, "error", "image is required"));
        String fromImage = URLEncoder.encode(image, StandardCharsets.UTF_8);
        String fromSrc = tag == null || tag.isBlank() ? "latest" : URLEncoder.encode(tag, StandardCharsets.UTF_8);
        return action("POST", "/images/create?fromImage=" + fromImage + "&tag=" + fromSrc, "", "pull-image", image + ":" + fromSrc);
    }

    @GetMapping("/images/{id}/inspect")
    public ResponseEntity<Map<String, Object>> inspectImage(@PathVariable String id) {
        return inspect("/images/" + id + "/json", "image", id);
    }

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

    @GetMapping("/volumes/{name}/inspect")
    public ResponseEntity<Map<String, Object>> inspectVolume(@PathVariable String name) {
        return inspect("/volumes/" + name, "volume", name);
    }

    @DeleteMapping("/volumes/{name}")
    public ResponseEntity<Map<String, Object>> removeVolume(@PathVariable String name) {
        return action("DELETE", "/volumes/" + name, null, "remove-volume", name);
    }

    @PostMapping("/volumes/prune")
    public ResponseEntity<Map<String, Object>> pruneVolumes() {
        return action("POST", "/volumes/prune", "{}", "prune-volumes", "unused");
    }

    // ── Network actions ───────────────────────────────────────────────────────

    @GetMapping("/networks/{id}/inspect")
    public ResponseEntity<Map<String, Object>> inspectNetwork(@PathVariable String id) {
        return inspect("/networks/" + id, "network", id);
    }

    @DeleteMapping("/networks/{id}")
    public ResponseEntity<Map<String, Object>> removeNetwork(@PathVariable String id) {
        return action("DELETE", "/networks/" + id, null, "remove-network", id);
    }

    @PostMapping("/networks/prune")
    public ResponseEntity<Map<String, Object>> pruneNetworks() {
        return action("POST", "/networks/prune", "{}", "prune-networks", "unused");
    }

    // ── System actions ────────────────────────────────────────────────────────

    @GetMapping("/system/df")
    public ResponseEntity<Map<String, Object>> systemDf() {
        return inspect("/system/df", "system-df", "docker");
    }

    @PostMapping("/system/prune")
    public ResponseEntity<Map<String, Object>> systemPrune(@RequestParam(defaultValue = "false") boolean volumes) {
        return action("POST", "/system/prune?volumes=" + volumes, "{}", "system-prune", "docker");
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> inspect(String path, String type, String target) {
        try {
            Map<String, Object> data = jsonMap(rawGet(path));
            return ResponseEntity.ok(Map.of("ok", true, "live", true, "type", type, "target", target, "data", data, "items", List.of(data)));
        } catch (Exception e) {
            return ResponseEntity.ok(errorPayload("inspect-" + type, target, e));
        }
    }

    private ResponseEntity<Map<String, Object>> action(String method, String path,
            String body, String action, String target) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("target", target);
        try {
            HttpSocketResponse response = rawRequest(method, path, body);
            boolean ok = response.status >= 200 && response.status < 300;
            payload.put("ok", ok);
            payload.put("live", true);
            payload.put("status", response.status);
            payload.put("body", parseMaybeJson(response.body));
            String correlationId = events.mutation("DOCKER_" + action.toUpperCase(Locale.ROOT).replace('-', '_'), target, ok, payload);
            payload.put("correlationId", correlationId);
            return ResponseEntity.ok(payload);
        } catch (Exception e) {
            payload.put("ok", false);
            payload.put("live", false);
            payload.put("state", classify(e));
            payload.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            String correlationId = events.mutation("DOCKER_" + action.toUpperCase(Locale.ROOT).replace('-', '_'), target, false, payload);
            payload.put("correlationId", correlationId);
            return ResponseEntity.ok(payload);
        }
    }

    private HttpSocketResponse rawRequest(String method, String path, String body) throws IOException {
        var address = UnixDomainSocketAddress.of(SOCKET);
        try (var channel = SocketChannel.open(address)) {
            byte[] bodyBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            StringBuilder req = new StringBuilder();
            req.append(method).append(' ').append(path).append(" HTTP/1.0\r\n");
            req.append("Host: localhost\r\n");
            req.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
            if (bodyBytes.length > 0) req.append("Content-Type: application/json\r\n");
            req.append("\r\n");
            channel.write(ByteBuffer.wrap(req.toString().getBytes(StandardCharsets.UTF_8)));
            if (bodyBytes.length > 0) channel.write(ByteBuffer.wrap(bodyBytes));
            String response = readAll(channel);
            int status = 200;
            if (response.startsWith("HTTP/")) {
                String firstLine = response.split("\r?\n", 2)[0];
                String[] parts = firstLine.split(" ");
                if (parts.length >= 2) status = Integer.parseInt(parts[1]);
            }
            int bodyStart = response.indexOf("\r\n\r\n");
            if (bodyStart < 0) bodyStart = response.indexOf("\n\n");
            String responseBody = bodyStart >= 0 ? response.substring(bodyStart + (response.charAt(bodyStart) == '\r' ? 4 : 2)).trim() : response;
            return new HttpSocketResponse(status, responseBody);
        }
    }

    private String rawGet(String path) throws IOException {
        return rawRequest("GET", path, null).body;
    }

    private String readAll(SocketChannel channel) throws IOException {
        var sb = new StringBuilder();
        var buf = ByteBuffer.allocate(65536);
        while (channel.read(buf) != -1) {
            buf.flip();
            sb.append(StandardCharsets.UTF_8.decode(buf));
            buf.clear();
        }
        return sb.toString();
    }

    private Map<String, Object> jsonMap(String raw) throws IOException {
        return mapper.readValue(raw, new TypeReference<>() {});
    }

    private Object parseMaybeJson(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try { return mapper.readValue(raw, Object.class); } catch (Exception ignored) { return raw; }
    }

    private Map<String, Object> summarizeStats(String id, Map<String, Object> stats) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("name", stats.getOrDefault("name", id));
        out.put("read", stats.get("read"));
        out.put("cpuPercent", cpuPercent(stats));
        out.put("memoryUsageBytes", nestedLong(stats, "memory_stats", "usage"));
        out.put("memoryLimitBytes", nestedLong(stats, "memory_stats", "limit"));
        out.put("pids", nestedLong(stats, "pids_stats", "current"));
        return out;
    }

    private double cpuPercent(Map<String, Object> stats) {
        double total = nestedDouble(stats, "cpu_stats", "cpu_usage", "total_usage");
        double preTotal = nestedDouble(stats, "precpu_stats", "cpu_usage", "total_usage");
        double system = nestedDouble(stats, "cpu_stats", "system_cpu_usage");
        double preSystem = nestedDouble(stats, "precpu_stats", "system_cpu_usage");
        double online = nestedDouble(stats, "cpu_stats", "online_cpus");
        if (online <= 0) online = 1;
        double cpuDelta = total - preTotal;
        double systemDelta = system - preSystem;
        if (cpuDelta <= 0 || systemDelta <= 0) return 0.0;
        return Math.round(((cpuDelta / systemDelta) * online * 100.0) * 100.0) / 100.0;
    }

    private long nestedLong(Map<String, Object> map, String... keys) {
        Object value = nested(map, keys);
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return 0L; }
    }

    private double nestedDouble(Map<String, Object> map, String... keys) {
        Object value = nested(map, keys);
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception ignored) { return 0.0; }
    }

    @SuppressWarnings("unchecked")
    private Object nested(Map<String, Object> map, String... keys) {
        Object value = map;
        for (String key : keys) {
            if (!(value instanceof Map<?,?> m)) return null;
            value = ((Map<String, Object>) m).get(key);
        }
        return value;
    }

    private Map<String, Object> errorPayload(String action, String target, Exception e) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", false);
        payload.put("live", false);
        payload.put("action", action);
        payload.put("target", target);
        payload.put("state", classify(e));
        payload.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        return payload;
    }

    private String classify(Exception e) {
        String msg = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
        if (msg.contains("permission")) return "DOCKER_SOCKET_PERMISSION_DENIED";
        if (msg.contains("no such file") || msg.contains("not found")) return "DOCKER_SOCKET_NOT_FOUND";
        if (msg.contains("connection refused")) return "DOCKER_DESKTOP_STOPPED";
        return "DOCKER_ENGINE_UNREACHABLE";
    }

    private List<String> parseDockerLogs(String raw) {
        List<String> lines = new ArrayList<>();
        for (String line : raw.split("\n")) {
            int start = 0;
            while (start < line.length() && line.charAt(start) < 32 && line.charAt(start) != 9) start++;
            String cleaned = line.substring(start).trim();
            if (!cleaned.isEmpty()) lines.add(cleaned);
        }
        return lines;
    }

    private record HttpSocketResponse(int status, String body) {}
}
