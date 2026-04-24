package dev.nebulaops.gateway.service;

import dev.nebulaops.gateway.client.DockerSocketClient;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * v23.2 — Docker runtime data via Docker Engine Unix socket.
 * The service returns live Docker API responses only. Empty collections mean the
 * engine returned no rows or the socket is unavailable; no seed/mock records are generated.
 */
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class DockerRuntimeService {

    private final DockerSocketClient socket;

    public DockerRuntimeService(DockerSocketClient socket) {
        this.socket = socket;
    }

    public Map<String, Object> status() { return socket.status(); }
    public Map<String, Object> containers() { return collection("containers", socket.containers()); }
    public Map<String, Object> images()     { return collection("images", socket.images()); }
    public Map<String, Object> volumes()    { return collection("volumes", socket.volumes()); }
    public Map<String, Object> networks()   { return collection("networks", socket.networks()); }

    public Map<String, Object> stats()  {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (ok) {
            for (Map<String, Object> container : socket.containers()) {
                String state = String.valueOf(container.getOrDefault("State", ""));
                if (!"running".equalsIgnoreCase(state)) continue;
                String id = String.valueOf(container.getOrDefault("Id", ""));
                if (id.isBlank()) continue;
                Map<String, Object> raw = socket.containerStats(id);
                if (raw.containsKey("error")) {
                    rows.add(Map.of("id", id, "name", firstContainerName(container), "live", false, "error", raw.get("error")));
                } else {
                    rows.add(statsRow(id, firstContainerName(container), raw));
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", ok);
        out.put("tool", "docker-socket");
        out.put("resource", "stats");
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("toolStatus", status);
        out.put("realDataOnly", true);
        return out;
    }

    public Map<String, Object> builds() { return Map.of("live", Boolean.TRUE.equals(status().get("ok")), "items", List.of(), "toolStatus", status(), "realDataOnly", true); }
    public Map<String, Object> scout()  { return Map.of("live", Boolean.TRUE.equals(status().get("ok")), "items", List.of(), "toolStatus", status(), "realDataOnly", true); }
    public Map<String, Object> events() {
        Map<String, Object> status = status();
        if (!Boolean.TRUE.equals(status.get("ok"))) return Map.of("live", false, "items", List.of(), "toolStatus", status, "realDataOnly", true);
        long until = System.currentTimeMillis() / 1000;
        long since = until - 3600;
        List<Map<String, Object>> rows = socket.events(since, until);
        return Map.of("live", true, "tool", "docker-socket", "resource", "events", "items", rows, "count", rows.size(), "toolStatus", status, "realDataOnly", true);
    }

    private Map<String, Object> collection(String resource, List<Map<String, Object>> items) {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        return Map.of("live", ok, "tool", "docker-socket", "resource", resource, "items", ok ? items : List.of(),
                      "count", ok ? items.size() : 0, "toolStatus", status, "realDataOnly", true);
    }

    private Map<String, Object> statsRow(String id, String name, Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("name", name);
        out.put("live", true);
        out.put("cpuPercent", cpuPercent(raw));
        out.put("memoryUsageBytes", nestedLong(raw, "memory_stats", "usage"));
        out.put("memoryLimitBytes", nestedLong(raw, "memory_stats", "limit"));
        out.put("pids", nestedLong(raw, "pids_stats", "current"));
        out.put("read", raw.get("read"));
        return out;
    }

    private String firstContainerName(Map<String, Object> raw) {
        Object names = raw.get("Names");
        if (names instanceof List list && !list.isEmpty()) return String.valueOf(list.get(0)).replaceFirst("^/+", "");
        String id = String.valueOf(raw.getOrDefault("Id", ""));
        return id.length() > 12 ? id.substring(0, 12) : id;
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

    private Object nested(Map<String, Object> map, String... keys) {
        Object value = map;
        for (String key : keys) {
            if (!(value instanceof Map<?,?> m)) return null;
            value = ((Map)m).get(key);
        }
        return value;
    }
}
