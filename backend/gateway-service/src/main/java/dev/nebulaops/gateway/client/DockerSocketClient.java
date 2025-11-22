package dev.nebulaops.gateway.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.Socket;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * v22.2-fixed — Talks directly to /var/run/docker.sock via Unix socket HTTP.
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
            return bodyStart >= 0 ? raw.substring(bodyStart + 4).trim() : raw;
        }
    }

    public boolean isAvailable() {
        try {
            var address = UnixDomainSocketAddress.of(SOCKET_PATH);
            try (var ch = SocketChannel.open(address)) { return ch.isConnected(); }
        } catch (Exception e) { return false; }
    }
}
