package dev.nebulaops.gateway.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * v23.3 — Talks directly to /var/run/docker.sock via Unix socket HTTP.
 * Bypasses the docker CLI binary entirely, so it works regardless of which
 * docker binary is on PATH (snap, wrapper, etc.).
 */
@Component
public class DockerSocketClient {

    private static final String SOCKET_PATH = "/var/run/docker.sock";
    private final ObjectMapper mapper = new ObjectMapper();

    /** GET /containers/json?all=true */
    public List<Map<String, Object>> containers() {
        return getList("/containers/json?all=true");
    }

    /** GET /images/json */
    public List<Map<String, Object>> images() {
        return getList("/images/json");
    }

    /** GET /volumes */
    public List<Map<String, Object>> volumes() {
        try {
            Map<String, Object> resp = getMap("/volumes");
            Object vols = resp.get("Volumes");
            if (vols instanceof List) {
                //noinspection unchecked
                return (List<Map<String, Object>>) vols;
            }
        } catch (Exception ignored) {}
        return List.of();
    }

    /** GET /networks */
    public List<Map<String, Object>> networks() {
        return getList("/networks");
    }

    /** GET /info — docker system info */
    public Map<String, Object> info() {
        return getMap("/info");
    }

    /** GET /containers/{id}/stats?stream=false — live per-container stats. */
    public Map<String, Object> containerStats(String id) {
        return getMap("/containers/" + safeSegment(id) + "/stats?stream=false");
    }

    /** GET /events with a bounded window so the request does not stream forever. */
    public List<Map<String, Object>> events(long sinceEpochSeconds, long untilEpochSeconds) {
        try {
            String body = get("/events?since=" + sinceEpochSeconds + "&until=" + untilEpochSeconds);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (String line : body.split("\n")) {
                if (line == null || line.isBlank()) continue;
                rows.add(mapper.readValue(line, new TypeReference<Map<String, Object>>() {}));
            }
            return rows;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** GET any Docker Engine object as a map. */
    public Map<String, Object> object(String path) {
        return getMap(path);
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private List<Map<String, Object>> getList(String path) {
        try {
            String body = get(path);
            return mapper.readValue(body, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> getMap(String path) {
        try {
            String body = get(path);
            return mapper.readValue(body, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Sends a minimal HTTP/1.0 GET over the Unix domain socket and returns the body.
     * Uses Java 16+ UnixDomainSocketAddress.
     */
    private String get(String path) throws IOException {
        var address = UnixDomainSocketAddress.of(SOCKET_PATH);
        try (var channel = SocketChannel.open(address)) {
            // Send HTTP request
            String request = "GET " + path + " HTTP/1.0\r\nHost: localhost\r\n\r\n";
            channel.write(java.nio.ByteBuffer.wrap(request.getBytes(StandardCharsets.UTF_8)));

            // Read full response
            var sb = new StringBuilder();
            var buf = java.nio.ByteBuffer.allocate(65536);
            while (channel.read(buf) != -1) {
                buf.flip();
                sb.append(StandardCharsets.UTF_8.decode(buf));
                buf.clear();
            }
            String raw = sb.toString();

            // Strip HTTP headers — body starts after blank line
            int bodyStart = raw.indexOf("\r\n\r\n");
            if (bodyStart == -1) bodyStart = raw.indexOf("\n\n");
            return bodyStart >= 0 ? raw.substring(bodyStart + (raw.charAt(bodyStart) == '\r' ? 4 : 2)).trim() : raw;
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("socket", SOCKET_PATH);
        out.put("checkedAt", Instant.now().toString());
        java.nio.file.Path socketPath = java.nio.file.Path.of(SOCKET_PATH);
        if (!java.nio.file.Files.exists(socketPath)) {
            out.put("ok", false);
            out.put("state", "DOCKER_SOCKET_NOT_FOUND");
            out.put("message", "Docker socket not reachable. Docker Desktop may be stopped or the socket is not mounted into the gateway container.");
            return out;
        }
        if (!java.nio.file.Files.isReadable(socketPath)) {
            out.put("ok", false);
            out.put("state", "DOCKER_SOCKET_PERMISSION_DENIED");
            out.put("message", "Docker socket exists but the gateway process cannot read it. Check Docker group membership or volume permissions.");
            return out;
        }
        try {
            Map<String, Object> info = info();
            boolean ok = !info.containsKey("error");
            out.put("ok", ok);
            out.put("state", ok ? "DOCKER_ENGINE_AVAILABLE" : "DOCKER_ENGINE_ERROR");
            out.put("message", ok ? "Docker Engine API is reachable through the Unix socket." : String.valueOf(info.get("error")));
            out.put("engine", info);
            return out;
        } catch (Exception e) {
            out.put("ok", false);
            out.put("state", classify(e));
            out.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
            return out;
        }
    }

    public boolean isAvailable() {
        Object ok = status().get("ok");
        return Boolean.TRUE.equals(ok);
    }

    private String classify(Exception e) {
        String msg = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
        if (msg.contains("permission")) return "DOCKER_SOCKET_PERMISSION_DENIED";
        if (msg.contains("no such file") || msg.contains("not found")) return "DOCKER_SOCKET_NOT_FOUND";
        if (msg.contains("connection refused")) return "DOCKER_DESKTOP_STOPPED";
        return "DOCKER_ENGINE_UNREACHABLE";
    }

    private String safeSegment(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9_.:@-]", "");
    }
}
