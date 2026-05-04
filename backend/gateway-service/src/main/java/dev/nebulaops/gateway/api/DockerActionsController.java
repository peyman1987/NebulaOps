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
 * v23.3 — Docker operational console endpoints.
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

    @GetMapping("/containers/{id}/top")
    public ResponseEntity<Map<String, Object>> containerTop(@PathVariable String id) {
        return inspect("/containers/" + id + "/top", "container-top", id);
    }

    @GetMapping("/containers/{id}/changes")
    public ResponseEntity<Map<String, Object>> containerChanges(@PathVariable String id) {
        try {
            List<Map<String, Object>> rows = mapper.readValue(rawGet("/containers/" + id + "/changes"), new TypeReference<>() {});
            return ResponseEntity.ok(Map.of("ok", true, "live", true, "type", "container-changes", "target", id, "items", rows, "count", rows.size()));
        } catch (Exception e) {
            return ResponseEntity.ok(errorPayload("container-changes", id, e));
        }
    }

    @GetMapping("/containers/{id}/health")
    public ResponseEntity<Map<String, Object>> containerHealth(@PathVariable String id) {
        try {
            Map<String, Object> inspect = jsonMap(rawGet("/containers/" + id + "/json"));
            Map<String, Object> state = asMap(inspect.get("State"));
            Object health = state.getOrDefault("Health", Map.of("Status", state.getOrDefault("Status", "unknown")));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", true);
            payload.put("live", true);
            payload.put("type", "container-health");
            payload.put("target", id);
            payload.put("state", state);
            payload.put("health", health);
            payload.put("items", List.of(health));
            return ResponseEntity.ok(payload);
        } catch (Exception e) {
            return ResponseEntity.ok(errorPayload("container-health", id, e));
        }
    }

    @GetMapping("/containers/{id}/details")
    public ResponseEntity<Map<String, Object>> containerDetails(@PathVariable String id,
            @RequestParam(defaultValue = "120") int tail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("live", true);
        payload.put("type", "container-details");
        payload.put("target", id);
        try {
            Map<String, Object> inspect = jsonMap(rawGet("/containers/" + id + "/json"));
            payload.put("inspect", inspect);
            try { payload.put("stats", summarizeStats(id, jsonMap(rawGet("/containers/" + id + "/stats?stream=false")))); } catch (Exception e) { payload.put("statsError", e.getMessage()); }
            try { payload.put("top", parseMaybeJson(rawGet("/containers/" + id + "/top"))); } catch (Exception e) { payload.put("topError", e.getMessage()); }
            try { payload.put("changes", mapper.readValue(rawGet("/containers/" + id + "/changes"), new TypeReference<List<Map<String, Object>>>() {})); } catch (Exception e) { payload.put("changesError", e.getMessage()); }
            try { payload.put("logs", parseDockerLogs(rawGet("/containers/" + id + "/logs?stdout=true&stderr=true&tail=" + Math.max(1, Math.min(tail, 1000)) + "&timestamps=true"))); } catch (Exception e) { payload.put("logsError", e.getMessage()); }
            payload.put("items", List.of(inspect));
            return ResponseEntity.ok(payload);
        } catch (Exception e) {
            return ResponseEntity.ok(errorPayload("container-details", id, e));
        }
    }

    @PostMapping("/containers/{id}/kill")
    public ResponseEntity<Map<String, Object>> killContainer(@PathVariable String id,
            @RequestParam(defaultValue = "SIGKILL") String signal) {
        String safeSignal = signal == null ? "SIGKILL" : signal.replaceAll("[^A-Za-z0-9_-]", "");
        if (safeSignal.isBlank()) safeSignal = "SIGKILL";
        return action("POST", "/containers/" + id + "/kill?signal=" + safeSignal, null, "kill", id);
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

    @GetMapping("/images/{id}/history")
    public ResponseEntity<Map<String, Object>> imageHistory(@PathVariable String id) {
        try {
            List<Map<String, Object>> rows = mapper.readValue(rawGet("/images/" + id + "/history"), new TypeReference<>() {});
            return ResponseEntity.ok(Map.of("ok", true, "live", true, "type", "image-history", "target", id, "items", rows, "count", rows.size()));
        } catch (Exception e) {
            return ResponseEntity.ok(errorPayload("image-history", id, e));
        }
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

    // ── Compose project actions ───────────────────────────────────────────────

    @PostMapping("/projects/{project}/start")
    public ResponseEntity<Map<String, Object>> startProject(@PathVariable String project) {
        return projectAction(project, "start", "POST", "start");
    }

    @PostMapping("/projects/{project}/stop")
    public ResponseEntity<Map<String, Object>> stopProject(@PathVariable String project,
            @RequestParam(defaultValue = "10") int t) {
        return projectAction(project, "stop", "POST", "stop?t=" + Math.max(0, Math.min(t, 120)));
    }

    @PostMapping("/projects/{project}/restart")
    public ResponseEntity<Map<String, Object>> restartProject(@PathVariable String project,
            @RequestParam(defaultValue = "5") int t) {
        return projectAction(project, "restart", "POST", "restart?t=" + Math.max(0, Math.min(t, 120)));
    }

    @GetMapping("/projects/{project}/logs")
    public ResponseEntity<Map<String, Object>> projectLogs(@PathVariable String project,
            @RequestParam(defaultValue = "120") int tail) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("live", true);
        payload.put("action", "project-logs");
        payload.put("project", project);
        List<Map<String, Object>> items = new ArrayList<>();
        try {
            for (Map<String, Object> c : containersForProject(project)) {
                String id = String.valueOf(c.getOrDefault("Id", ""));
                if (id.isBlank()) continue;
                String name = firstName(c);
                String path = "/containers/" + id + "/logs?stdout=true&stderr=true&tail=" + Math.max(1, Math.min(tail, 1000)) + "&timestamps=true";
                try {
                    items.add(Map.of("id", id, "name", name, "logs", parseDockerLogs(rawGet(path))));
                } catch (Exception e) {
                    items.add(Map.of("id", id, "name", name, "error", e.getMessage()));
                    payload.put("ok", false);
                }
            }
            payload.put("items", items);
            payload.put("count", items.size());
            return ResponseEntity.ok(payload);
        } catch (Exception e) {
            return ResponseEntity.ok(errorPayload("project-logs", project, e));
        }
    }

    // ── Troubleshooting and root-cause analysis ──────────────────────────────

    @GetMapping("/projects/{project}/health-report")
    public ResponseEntity<Map<String, Object>> projectHealthReport(@PathVariable String project,
            @RequestParam(defaultValue = "120") int tail) {
        return ResponseEntity.ok(projectTroubleshootingReport(project, tail, false, "project-health-report"));
    }

    @GetMapping("/projects/{project}/failure-analysis")
    public ResponseEntity<Map<String, Object>> projectFailureAnalysis(@PathVariable String project,
            @RequestParam(defaultValue = "300") int tail) {
        return ResponseEntity.ok(projectTroubleshootingReport(project, tail, true, "project-failure-analysis"));
    }

    @GetMapping("/projects/{project}/startup-order")
    public ResponseEntity<Map<String, Object>> projectStartupOrder(@PathVariable String project) {
        try {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> c : containersForProject(project)) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", String.valueOf(c.getOrDefault("Id", "")));
                row.put("name", firstName(c));
                row.put("service", label(c, "com.docker.compose.service"));
                row.put("project", label(c, "com.docker.compose.project"));
                row.put("created", c.get("Created"));
                row.put("state", c.get("State"));
                row.put("status", c.get("Status"));
                row.put("image", c.get("Image"));
                items.add(row);
            }
            items.sort(Comparator.comparing(r -> String.valueOf(r.getOrDefault("created", "0"))));
            Map<String, Object> out = livePayload("project-startup-order", project, items);
            out.put("message", "Startup order is derived from live Docker container Created timestamps and Compose labels.");
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.ok(errorPayload("project-startup-order", project, e));
        }
    }

    @GetMapping("/projects/{project}/dependency-map")
    public ResponseEntity<Map<String, Object>> projectDependencyMap(@PathVariable String project) {
        try {
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<Map<String, Object>> edges = new ArrayList<>();
            for (Map<String, Object> c : containersForProject(project)) {
                String id = String.valueOf(c.getOrDefault("Id", ""));
                String name = firstName(c);
                String service = label(c, "com.docker.compose.service");
                if (service.isBlank()) service = name;
                nodes.add(node("container", id, name, Map.of("service", service, "state", c.getOrDefault("State", ""), "image", c.getOrDefault("Image", ""))));
                Object ports = c.get("Ports");
                if (ports instanceof List<?> rows) {
                    for (Object po : rows) if (po instanceof Map<?,?> port) {
                        String publicPort = String.valueOf(port.get("PublicPort") == null ? "" : port.get("PublicPort"));
                        String privatePort = String.valueOf(port.get("PrivatePort") == null ? "" : port.get("PrivatePort"));
                        if (!publicPort.isBlank() && !"0".equals(publicPort)) {
                            String portId = "port:" + publicPort;
                            nodes.add(node("port", portId, publicPort + ":" + privatePort, Map.of("protocol", String.valueOf(port.get("Type") == null ? "tcp" : port.get("Type")))));
                            edges.add(edge(id, portId, "publishes"));
                        }
                    }
                }
                for (String network : networksOfContainerSummary(c)) {
                    String networkId = "network:" + network;
                    nodes.add(node("network", networkId, network, Map.of()));
                    edges.add(edge(id, networkId, "attached-to"));
                }
                if (!id.isBlank()) {
                    try {
                        Map<String, Object> inspect = jsonMap(rawGet("/containers/" + id + "/json"));
                        Map<String, Object> hostConfig = asMap(inspect.get("HostConfig"));
                        Object mounts = inspect.get("Mounts");
                        if (mounts instanceof List<?> mountRows) {
                            for (Object mo : mountRows) if (mo instanceof Map<?,?> mount) {
                                Object destinationValue = mount.get("Destination") != null ? mount.get("Destination") : (mount.get("Name") != null ? mount.get("Name") : "mount");
                                String destination = String.valueOf(destinationValue);
                                String mountId = "mount:" + destination;
                                nodes.add(node("mount", mountId, destination, Map.of("type", String.valueOf(mount.get("Type") == null ? "" : mount.get("Type")))));
                                edges.add(edge(id, mountId, "mounts"));
                            }
                        }
                        Object links = hostConfig.get("Links");
                        if (links instanceof List<?> linkRows) for (Object link : linkRows) edges.add(edge(id, "container:" + link, "links"));
                    } catch (Exception ignored) { }
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true); out.put("live", true); out.put("type", "project-dependency-map"); out.put("project", project);
            out.put("nodes", dedupeById(nodes)); out.put("edges", edges); out.put("items", dedupeById(nodes)); out.put("realDataOnly", true);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.ok(errorPayload("project-dependency-map", project, e));
        }
    }

    @GetMapping("/projects/{project}/log-analysis")
    public ResponseEntity<Map<String, Object>> projectLogAnalysis(@PathVariable String project,
            @RequestParam(defaultValue = "500") int tail) {
        try {
            List<Map<String, Object>> findings = new ArrayList<>();
            List<Map<String, Object>> sources = new ArrayList<>();
            for (Map<String, Object> c : containersForProject(project)) {
                String id = String.valueOf(c.getOrDefault("Id", ""));
                if (id.isBlank()) continue;
                String name = firstName(c);
                try {
                    List<String> lines = parseDockerLogs(rawGet("/containers/" + id + "/logs?stdout=true&stderr=true&tail=" + Math.max(1, Math.min(tail, 3000)) + "&timestamps=true"));
                    sources.add(Map.of("id", id, "name", name, "lineCount", lines.size()));
                    findings.addAll(logFindings(project, id, name, lines));
                } catch (Exception e) {
                    findings.add(finding("WARN", "LOGS_UNAVAILABLE", name, e.getMessage(), List.of()));
                }
            }
            Map<String, Object> out = livePayload("project-log-analysis", project, findings);
            out.put("sources", sources);
            out.put("summary", severitySummary(findings));
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.ok(errorPayload("project-log-analysis", project, e));
        }
    }

    @GetMapping("/containers/{id}/log-analysis")
    public ResponseEntity<Map<String, Object>> containerLogAnalysis(@PathVariable String id,
            @RequestParam(defaultValue = "500") int tail) {
        try {
            List<String> lines = parseDockerLogs(rawGet("/containers/" + id + "/logs?stdout=true&stderr=true&tail=" + Math.max(1, Math.min(tail, 3000)) + "&timestamps=true"));
            List<Map<String, Object>> findings = logFindings("container", id, id, lines);
            Map<String, Object> out = livePayload("container-log-analysis", id, findings);
            out.put("lineCount", lines.size());
            out.put("summary", severitySummary(findings));
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.ok(errorPayload("container-log-analysis", id, e));
        }
    }

    @GetMapping("/timeline")
    public ResponseEntity<Map<String, Object>> dockerTimeline(@RequestParam(required = false) String project,
            @RequestParam(defaultValue = "3600") long windowSeconds) {
        try {
            long now = System.currentTimeMillis() / 1000L;
            long since = Math.max(0L, now - Math.max(60L, Math.min(windowSeconds, 86400L)));
            String raw = rawGet("/events?since=" + since + "&until=" + now);
            List<Map<String, Object>> events = new ArrayList<>();
            for (String line : raw.split("\n")) {
                if (line == null || line.isBlank()) continue;
                Map<String, Object> ev = mapper.readValue(line, new TypeReference<>() {});
                if (project == null || project.isBlank() || eventMatchesProject(ev, project)) events.add(normalizeDockerEvent(ev));
            }
            events.sort(Comparator.comparing(r -> String.valueOf(r.getOrDefault("time", ""))));
            Map<String, Object> out = livePayload("docker-timeline", project == null ? "all" : project, events);
            out.put("windowSeconds", windowSeconds);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.ok(errorPayload("docker-timeline", project == null ? "all" : project, e));
        }
    }

    private Map<String, Object> projectTroubleshootingReport(String project, int tail, boolean includeLogs, String type) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> containers = new ArrayList<>();
        List<Map<String, Object>> findings = new ArrayList<>();
        try {
            for (Map<String, Object> c : containersForProject(project)) {
                String id = String.valueOf(c.getOrDefault("Id", ""));
                String name = firstName(c);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id); row.put("name", name); row.put("service", label(c, "com.docker.compose.service"));
                row.put("image", c.get("Image")); row.put("state", c.get("State")); row.put("status", c.get("Status"));
                if (!id.isBlank()) {
                    try {
                        Map<String, Object> inspect = jsonMap(rawGet("/containers/" + id + "/json"));
                        Map<String, Object> state = asMap(inspect.get("State"));
                        row.put("health", asMap(state.get("Health")).getOrDefault("Status", state.getOrDefault("Status", "unknown")));
                        row.put("restartCount", inspect.getOrDefault("RestartCount", 0));
                        analyzeContainerState(findings, project, name, c, inspect);
                        if (includeLogs) {
                            List<String> logs = parseDockerLogs(rawGet("/containers/" + id + "/logs?stdout=true&stderr=true&tail=" + Math.max(1, Math.min(tail, 3000)) + "&timestamps=true"));
                            row.put("logLines", logs.size());
                            findings.addAll(logFindings(project, id, name, logs));
                        }
                    } catch (Exception e) {
                        findings.add(finding("WARN", "INSPECT_UNAVAILABLE", name, e.getMessage(), List.of()));
                    }
                }
                containers.add(row);
            }
            out.put("ok", true); out.put("live", true); out.put("type", type); out.put("project", project);
            out.put("containers", containers); out.put("items", findings); out.put("count", findings.size());
            out.put("summary", severitySummary(findings)); out.put("realDataOnly", true);
            return out;
        } catch (Exception e) {
            return errorPayload(type, project, e);
        }
    }

    private void analyzeContainerState(List<Map<String, Object>> findings, String project, String name, Map<String, Object> summary, Map<String, Object> inspect) {
        String state = String.valueOf(summary.getOrDefault("State", "")).toLowerCase(Locale.ROOT);
        String statusText = String.valueOf(summary.getOrDefault("Status", ""));
        if ("exited".equals(state) || "dead".equals(state)) findings.add(finding("ERROR", "CONTAINER_NOT_RUNNING", name, statusText, List.of()));
        if ("restarting".equals(state)) findings.add(finding("ERROR", "RESTART_LOOP", name, statusText, List.of()));
        if (statusText.toLowerCase(Locale.ROOT).contains("unhealthy")) findings.add(finding("ERROR", "HEALTHCHECK_UNHEALTHY", name, statusText, List.of()));
        Map<String, Object> stateMap = asMap(inspect.get("State"));
        if (!String.valueOf(stateMap.getOrDefault("Error", "")).isBlank()) findings.add(finding("ERROR", "CONTAINER_STATE_ERROR", name, String.valueOf(stateMap.get("Error")), List.of()));
        int exitCode = 0;
        Object ec = stateMap.get("ExitCode");
        if (ec instanceof Number n) exitCode = n.intValue(); else try { exitCode = Integer.parseInt(String.valueOf(ec)); } catch (Exception ignored) {}
        if (exitCode != 0) findings.add(finding("ERROR", "NON_ZERO_EXIT_CODE", name, "Container exit code " + exitCode, List.of()));
        Object restartCount = inspect.get("RestartCount");
        int rc = 0;
        if (restartCount instanceof Number n) rc = n.intValue(); else try { rc = Integer.parseInt(String.valueOf(restartCount)); } catch (Exception ignored) {}
        if (rc >= 3) findings.add(finding("WARN", "HIGH_RESTART_COUNT", name, "Container restarted " + rc + " times", List.of()));
    }

    private List<Map<String, Object>> logFindings(String project, String id, String name, List<String> lines) {
        List<Map<String, Object>> findings = new ArrayList<>();
        String[][] patterns = {
            {"ERROR", "EXCEPTION", "exception"}, {"ERROR", "ERROR", "error"}, {"WARN", "WARNING", "warn"},
            {"ERROR", "CONNECTION_REFUSED", "connection refused"}, {"ERROR", "ADDRESS_IN_USE", "address already in use"},
            {"ERROR", "PERMISSION_DENIED", "permission denied"}, {"ERROR", "TIMEOUT", "timeout"},
            {"ERROR", "NO_SUCH_FILE", "no such file"}, {"ERROR", "OOM", "out of memory"},
            {"ERROR", "AUTHENTICATION_FAILED", "authentication failed"}
        };
        for (String[] p : patterns) {
            List<String> evidence = new ArrayList<>();
            for (String line : lines) {
                if (line.toLowerCase(Locale.ROOT).contains(p[2])) {
                    evidence.add(line);
                    if (evidence.size() >= 5) break;
                }
            }
            if (!evidence.isEmpty()) findings.add(finding(p[0], p[1], name, "Log pattern detected in live container logs", evidence));
        }
        return findings;
    }

    private Map<String, Object> finding(String severity, String reason, String target, String message, List<String> evidence) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", reason + ":" + target); row.put("severity", severity); row.put("reason", reason); row.put("target", target);
        row.put("message", message == null ? "" : message); row.put("evidence", evidence == null ? List.of() : evidence); row.put("source", "docker-engine-api");
        return row;
    }

    private Map<String, Object> severitySummary(List<Map<String, Object>> findings) {
        long errors = findings.stream().filter(f -> "ERROR".equals(f.get("severity"))).count();
        long warnings = findings.stream().filter(f -> "WARN".equals(f.get("severity"))).count();
        return Map.of("total", findings.size(), "errors", errors, "warnings", warnings, "ok", errors == 0);
    }

    private Map<String, Object> livePayload(String type, String target, List<Map<String, Object>> items) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true); out.put("live", true); out.put("type", type); out.put("target", target);
        out.put("items", items); out.put("count", items.size()); out.put("realDataOnly", true);
        return out;
    }

    private Map<String, Object> node(String type, String id, String name, Map<String, Object> meta) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id); row.put("type", type); row.put("name", name); row.put("meta", meta);
        return row;
    }

    private Map<String, Object> edge(String from, String to, String relation) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("from", from); row.put("to", to); row.put("relation", relation);
        return row;
    }

    private List<Map<String, Object>> dedupeById(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) byId.putIfAbsent(String.valueOf(row.get("id")), row);
        return new ArrayList<>(byId.values());
    }

    private List<String> networksOfContainerSummary(Map<String, Object> c) {
        List<String> out = new ArrayList<>();
        Object networkSettings = c.get("NetworkSettings");
        if (networkSettings instanceof Map<?,?> ns) {
            Object networks = ns.get("Networks");
            if (networks instanceof Map<?,?> map) for (Object key : map.keySet()) out.add(String.valueOf(key));
        }
        return out;
    }

    private boolean eventMatchesProject(Map<String, Object> event, String project) {
        Map<String, Object> actor = asMap(event.get("Actor"));
        Map<String, Object> attrs = asMap(actor.get("Attributes"));
        return project.equals(attrs.get("com.docker.compose.project")) || project.equals(attrs.get("com.docker.stack.namespace"));
    }

    private Map<String, Object> normalizeDockerEvent(Map<String, Object> ev) {
        Map<String, Object> actor = asMap(ev.get("Actor"));
        Map<String, Object> attrs = asMap(actor.get("Attributes"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", String.valueOf(ev.getOrDefault("timeNano", ev.getOrDefault("time", UUID.randomUUID().toString()))));
        row.put("time", ev.get("time")); row.put("type", ev.get("Type")); row.put("action", ev.get("Action"));
        row.put("container", attrs.getOrDefault("name", actor.getOrDefault("ID", "")));
        row.put("service", attrs.getOrDefault("com.docker.compose.service", ""));
        row.put("project", attrs.getOrDefault("com.docker.compose.project", attrs.getOrDefault("com.docker.stack.namespace", "")));
        row.put("source", "docker-engine-api");
        return row;
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> projectAction(String project, String actionName, String method, String dockerOperationPath) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", "project-" + actionName);
        payload.put("project", project);
        List<Map<String, Object>> results = new ArrayList<>();
        boolean ok = true;
        try {
            List<Map<String, Object>> containers = containersForProject(project);
            for (Map<String, Object> c : containers) {
                String id = String.valueOf(c.getOrDefault("Id", ""));
                if (id.isBlank()) continue;
                HttpSocketResponse response = rawRequest(method, "/containers/" + id + "/" + dockerOperationPath, null);
                boolean rowOk = response.status >= 200 && response.status < 300;
                ok = ok && rowOk;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("name", firstName(c));
                row.put("service", label(c, "com.docker.compose.service"));
                row.put("status", response.status);
                row.put("ok", rowOk);
                row.put("body", parseMaybeJson(response.body));
                results.add(row);
            }
            payload.put("ok", ok);
            payload.put("live", true);
            payload.put("items", results);
            payload.put("count", results.size());
            payload.put("correlationId", events.mutation("DOCKER_PROJECT_" + actionName.toUpperCase(Locale.ROOT), project, ok, payload));
            return ResponseEntity.ok(payload);
        } catch (Exception e) {
            return ResponseEntity.ok(errorPayload("project-" + actionName, project, e));
        }
    }

    private List<Map<String, Object>> containersForProject(String project) throws IOException {
        List<Map<String, Object>> rows = mapper.readValue(rawGet("/containers/json?all=true"), new TypeReference<>() {});
        List<Map<String, Object>> out = new ArrayList<>();
        String requested = project == null ? "" : project.trim();
        for (Map<String, Object> c : rows) {
            String composeProject = label(c, "com.docker.compose.project");
            String stackNamespace = label(c, "com.docker.stack.namespace");
            if (requested.equals(composeProject) || requested.equals(stackNamespace) || ("standalone".equals(requested) && composeProject.isBlank() && stackNamespace.isBlank())) {
                out.add(c);
            }
        }
        return out;
    }

    private String label(Map<String, Object> c, String key) {
        Object labels = c.get("Labels");
        if (labels instanceof Map<?,?> m) {
            Object value = m.get(key);
            return value == null ? "" : String.valueOf(value);
        }
        return "";
    }

    private String firstName(Map<String, Object> c) {
        Object names = c.get("Names");
        if (names instanceof List<?> list && !list.isEmpty()) return String.valueOf(list.get(0)).replaceFirst("^/+", "");
        String id = String.valueOf(c.getOrDefault("Id", ""));
        return id.length() > 12 ? id.substring(0, 12) : id;
    }

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?,?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
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
