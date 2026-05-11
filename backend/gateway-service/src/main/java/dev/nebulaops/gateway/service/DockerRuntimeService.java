package dev.nebulaops.gateway.service;

import dev.nebulaops.gateway.client.DockerSocketClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * v23.4 — Docker runtime data via Docker Engine Unix socket.
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

    public Map<String, Object> info()       { return liveObject("info", socket.info()); }
    public Map<String, Object> version()    { return liveObject("version", socket.object("/version")); }
    public Map<String, Object> systemDf()   { return liveObject("system-df", socket.object("/system/df")); }

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

    /** Compose/project view derived only from live Docker container labels. */
    public Map<String, Object> projects() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        Map<String, Map<String, Object>> projects = new TreeMap<>();
        if (ok) {
            for (Map<String, Object> c : socket.containers()) {
                Map labels = labels(c);
                String project = firstNonBlank(
                    str(labels.get("com.docker.compose.project")),
                    str(labels.get("com.docker.stack.namespace")),
                    "standalone"
                );
                String service = firstNonBlank(
                    str(labels.get("com.docker.compose.service")),
                    serviceFromName(firstContainerName(c))
                );
                Map<String, Object> row = projects.computeIfAbsent(project, key -> newProject(key));
                List<Map<String, Object>> containers = (List<Map<String, Object>>) row.get("containers");
                containers.add(projectContainerRow(c, service));
                addUnique((List<String>) row.get("services"), service);
                addUnique((List<String>) row.get("images"), str(c.get("Image")));
                addUnique((List<String>) row.get("networks"), networksOf(c));
                String state = str(c.get("State")).toLowerCase(Locale.ROOT);
                row.put("running", ((Number) row.get("running")).intValue() + ("running".equals(state) ? 1 : 0));
                row.put("exited", ((Number) row.get("exited")).intValue() + ("exited".equals(state) ? 1 : 0));
                row.put("unhealthy", ((Number) row.get("unhealthy")).intValue() + (String.valueOf(c.getOrDefault("Status", "")).toLowerCase(Locale.ROOT).contains("unhealthy") ? 1 : 0));
            }
        }
        List<Map<String, Object>> items = new ArrayList<>(projects.values());
        for (Map<String, Object> project : items) {
            project.put("containerCount", ((List) project.get("containers")).size());
            project.put("status", projectStatus(project));
        }
        Map<String, Object> out = base("compose-projects", ok, status);
        out.put("items", items);
        out.put("count", items.size());
        out.put("generatedAt", Instant.now().toString());
        return out;
    }

    /** Single Compose/project detail derived from live Docker labels. */
    public Map<String, Object> project(String projectName) {
        Map<String, Object> all = projects();
        String requested = str(projectName).trim();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> project : itemsFrom(all.get("items"))) {
            String name = str(project.get("name"));
            if (requested.isBlank() || requested.equals(name)) rows.add(project);
        }
        Map<String, Object> out = base("compose-project", Boolean.TRUE.equals(all.get("live")), (Map<String, Object>) all.getOrDefault("toolStatus", status()));
        out.put("project", requested);
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("realDataOnly", true);
        return out;
    }

    /** Host port conflict detector from live Docker container port mappings. */
    public Map<String, Object> portConflicts() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        Map<String, List<Map<String, Object>>> byPort = new TreeMap<>();
        if (ok) {
            for (Map<String, Object> c : socket.containers()) {
                for (String port : publicPortsOf(c)) {
                    byPort.computeIfAbsent(port, ignored -> new ArrayList<>()).add(Map.of(
                        "id", str(c.get("Id")),
                        "name", firstContainerName(c),
                        "image", str(c.get("Image")),
                        "state", str(c.get("State")),
                        "status", str(c.get("Status"))
                    ));
                }
            }
        }
        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : byPort.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflicts.add(Map.of(
                    "id", "port:" + entry.getKey(),
                    "name", entry.getKey(),
                    "port", entry.getKey(),
                    "status", "CONFLICT",
                    "containers", entry.getValue(),
                    "message", "Multiple containers report the same published host port."
                ));
            }
        }
        Map<String, Object> out = base("port-conflicts", ok, status);
        out.put("items", conflicts);
        out.put("count", conflicts.size());
        out.put("summary", Map.of("publishedPorts", byPort.size(), "conflicts", conflicts.size()));
        return out;
    }

    /** Operational diagnostics derived from the live Docker Engine API. */
    public Map<String, Object> diagnostics() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        List<Map<String, Object>> containers = ok ? socket.containers() : List.of();
        List<Map<String, Object>> images = ok ? socket.images() : List.of();
        List<Map<String, Object>> volumes = ok ? socket.volumes() : List.of();
        List<Map<String, Object>> networks = ok ? socket.networks() : List.of();
        Map<String, Object> info = ok ? socket.info() : Map.of();
        Map<String, Object> df = ok ? socket.object("/system/df") : Map.of();

        long running = containers.stream().filter(c -> "running".equalsIgnoreCase(str(c.get("State")))).count();
        long exited = containers.stream().filter(c -> "exited".equalsIgnoreCase(str(c.get("State")))).count();
        long unhealthy = containers.stream().filter(c -> str(c.get("Status")).toLowerCase(Locale.ROOT).contains("unhealthy")).count();
        long restarting = containers.stream().filter(c -> "restarting".equalsIgnoreCase(str(c.get("State")))).count();
        long danglingImages = images.stream().filter(this::isDanglingImage).count();

        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(check("Docker socket", ok ? "OK" : "ERROR", str(status.getOrDefault("state", "UNKNOWN")), status));
        checks.add(check("Docker daemon", ok ? "OK" : "ERROR", ok ? "Docker Engine API reachable" : str(status.getOrDefault("message", "Unavailable")), Map.of("engine", info)));
        checks.add(check("Running containers", running > 0 ? "OK" : "WARN", running + " running / " + containers.size() + " total", Map.of("running", running, "total", containers.size())));
        checks.add(check("Unhealthy containers", unhealthy == 0 ? "OK" : "ERROR", unhealthy + " unhealthy", Map.of("unhealthy", unhealthy)));
        checks.add(check("Restarting containers", restarting == 0 ? "OK" : "WARN", restarting + " restarting", Map.of("restarting", restarting)));
        checks.add(check("Exited containers", exited == 0 ? "OK" : "WARN", exited + " exited", Map.of("exited", exited)));
        checks.add(check("Dangling images", danglingImages == 0 ? "OK" : "WARN", danglingImages + " dangling", Map.of("danglingImages", danglingImages)));
        checks.add(check("Docker disk usage", ok ? "OK" : "ERROR", ok ? "Docker /system/df returned live data" : "Docker disk usage unavailable", df));
        Map<String, Object> conflicts = portConflicts();
        int conflictCount = ((Number) conflicts.getOrDefault("count", 0)).intValue();
        checks.add(check("Published port conflicts", conflictCount == 0 ? "OK" : "ERROR", conflictCount + " conflicting published host ports", conflicts));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("containers", containers.size());
        summary.put("running", running);
        summary.put("exited", exited);
        summary.put("unhealthy", unhealthy);
        summary.put("restarting", restarting);
        summary.put("images", images.size());
        summary.put("danglingImages", danglingImages);
        summary.put("volumes", volumes.size());
        summary.put("networks", networks.size());
        summary.put("serverVersion", str(info.getOrDefault("ServerVersion", "")));
        summary.put("storageDriver", str(info.getOrDefault("Driver", "")));
        summary.put("publishedPortConflicts", conflictCount);

        Map<String, Object> out = base("diagnostics", ok, status);
        out.put("summary", summary);
        out.put("items", checks);
        out.put("count", checks.size());
        out.put("systemDf", df);
        out.put("realDataOnly", true);
        return out;
    }

    /** Graph nodes and edges derived from live Docker containers/images/networks/volumes. */
    public Map<String, Object> topology() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        if (ok) {
            Set<String> nodeIds = new LinkedHashSet<>();
            for (Map<String, Object> c : socket.containers()) {
                String cid = "container:" + shortId(str(c.get("Id")));
                addNode(nodes, nodeIds, cid, firstContainerName(c), "container", str(c.get("State")));
                String image = str(c.get("Image"));
                if (!image.isBlank()) {
                    String iid = "image:" + image;
                    addNode(nodes, nodeIds, iid, image, "image", "referenced");
                    addEdge(edges, cid, iid, "uses image");
                }
                for (String network : networksOf(c)) {
                    String nid = "network:" + network;
                    addNode(nodes, nodeIds, nid, network, "network", "attached");
                    addEdge(edges, cid, nid, "attached to");
                }
                for (String volume : mountsOf(c)) {
                    String vid = "volume:" + volume;
                    addNode(nodes, nodeIds, vid, volume, "volume", "mounted");
                    addEdge(edges, cid, vid, "mounts");
                }
            }
        }
        Map<String, Object> out = base("topology", ok, status);
        out.put("nodes", nodes);
        out.put("edges", edges);
        out.put("items", nodes);
        out.put("count", nodes.size());
        return out;
    }

    /** Preview only. It never executes prune and is populated from Docker /system/df. */
    public Map<String, Object> prunePreview() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        Map<String, Object> df = ok ? socket.object("/system/df") : Map.of();
        List<Map<String, Object>> items = new ArrayList<>();
        if (ok) {
            addDfSection(items, "Images", df.get("Images"));
            addDfSection(items, "Containers", df.get("Containers"));
            addDfSection(items, "Volumes", df.get("Volumes"));
            addDfSection(items, "BuildCache", df.get("BuildCache"));
        }
        Map<String, Object> out = base("prune-preview", ok, status);
        out.put("items", items);
        out.put("count", items.size());
        out.put("systemDf", df);
        out.put("note", "Preview only: no prune command is executed by this endpoint.");
        return out;
    }

    /** Live Docker build cache entries from /system/df. */
    public Map<String, Object> buildCache() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        Map<String, Object> df = ok ? socket.object("/system/df") : Map.of();
        List<Map<String, Object>> items = new ArrayList<>();
        if (ok) addDfSection(items, "BuildCache", df.get("BuildCache"));
        long totalSize = items.stream().mapToLong(row -> longValue(row.get("size"))).sum();
        Map<String, Object> out = base("build-cache", ok, status);
        out.put("items", items);
        out.put("count", items.size());
        out.put("summary", Map.of("entries", items.size(), "sizeBytes", totalSize));
        out.put("systemDf", df);
        return out;
    }

    /** Live Docker volume usage entries from /system/df. */
    public Map<String, Object> volumeUsage() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        Map<String, Object> df = ok ? socket.object("/system/df") : Map.of();
        List<Map<String, Object>> items = new ArrayList<>();
        if (ok) addDfSection(items, "Volumes", df.get("Volumes"));
        long totalSize = items.stream().mapToLong(row -> longValue(row.get("size"))).sum();
        Map<String, Object> out = base("volume-usage", ok, status);
        out.put("items", items);
        out.put("count", items.size());
        out.put("summary", Map.of("volumes", items.size(), "sizeBytes", totalSize));
        out.put("systemDf", df);
        return out;
    }

    /** Per-container CPU/memory pressure from live Docker stats. */
    public Map<String, Object> resourcePressure() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (ok) {
            for (Map<String, Object> c : socket.containers()) {
                String id = str(c.get("Id"));
                if (id.isBlank() || !"running".equalsIgnoreCase(str(c.get("State")))) continue;
                Map<String, Object> raw = socket.containerStats(id);
                Map<String, Object> row = statsRow(id, firstContainerName(c), raw);
                row.put("image", str(c.get("Image")));
                row.put("state", str(c.get("State")));
                row.put("status", str(c.get("Status")));
                row.put("memoryPercent", memoryPercent(row));
                row.put("pressure", pressureLevel(row));
                rows.add(row);
            }
            rows.sort(Comparator.comparing((Map<String, Object> r) -> doubleValue(r.get("memoryPercent"))).reversed());
        }
        Map<String, Object> out = base("resource-pressure", ok, status);
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("summary", pressureSummary(rows));
        return out;
    }

    /** Compose/project risk summary derived from live project state, diagnostics and port conflicts. */
    public Map<String, Object> projectRisks() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        List<Map<String, Object>> items = new ArrayList<>();
        if (ok) {
            for (Map<String, Object> project : itemsFrom(projects().get("items"))) {
                String projectName = str(project.get("name"));
                String projectStatus = str(project.get("status"));
                int unhealthy = intValue(project.get("unhealthy"));
                int running = intValue(project.get("running"));
                int total = project.get("containers") instanceof List<?> list ? list.size() : 0;
                if (!"RUNNING".equals(projectStatus)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", "project:" + projectName);
                    row.put("name", projectName);
                    row.put("type", "compose-project");
                    row.put("severity", unhealthy > 0 ? "ERROR" : "WARN");
                    row.put("status", projectStatus);
                    row.put("message", running + "/" + total + " containers are running");
                    row.put("source", "docker-labels");
                    row.put("details", project);
                    items.add(row);
                }
            }
            for (Map<String, Object> conflict : itemsFrom(portConflicts().get("items"))) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", str(conflict.get("id")));
                row.put("name", str(conflict.get("name")));
                row.put("type", "published-port-conflict");
                row.put("severity", "ERROR");
                row.put("status", str(conflict.get("status")));
                row.put("message", str(conflict.get("message")));
                row.put("source", "docker-engine-api");
                row.put("details", conflict);
                items.add(row);
            }
        }
        Map<String, Object> out = base("project-risks", ok, status);
        out.put("items", items);
        out.put("count", items.size());
        out.put("summary", riskSummary(items));
        return out;
    }


    /** Published port exposure matrix from live container port bindings. */
    public Map<String, Object> networkExposure() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (ok) {
            for (Map<String, Object> c : socket.containers()) {
                Map labels = labels(c);
                Object ports = c.get("Ports");
                if (!(ports instanceof List<?> portRows)) continue;
                for (Object po : portRows) {
                    if (!(po instanceof Map<?,?> p)) continue;
                    Object publicPort = p.get("PublicPort");
                    if (publicPort == null || String.valueOf(publicPort).isBlank() || "0".equals(String.valueOf(publicPort))) continue;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", shortId(str(c.get("Id"))) + ":" + publicPort);
                    row.put("containerId", str(c.get("Id")));
                    row.put("container", firstContainerName(c));
                    row.put("project", firstNonBlank(str(labels.get("com.docker.compose.project")), str(labels.get("com.docker.stack.namespace")), "standalone"));
                    row.put("service", firstNonBlank(str(labels.get("com.docker.compose.service")), serviceFromName(firstContainerName(c))));
                    row.put("image", str(c.get("Image")));
                    row.put("state", str(c.get("State")));
                    row.put("ip", str(p.get("IP")));
                    row.put("publicPort", publicPort);
                    row.put("privatePort", p.get("PrivatePort"));
                    row.put("protocol", str(p.containsKey("Type") ? p.get("Type") : "tcp"));
                    row.put("severity", "WARN");
                    row.put("source", "docker-engine-api");
                    rows.add(row);
                }
            }
            rows.sort(Comparator.comparing(r -> str(r.get("project")) + "/" + str(r.get("service")) + "/" + str(r.get("publicPort"))));
        }
        Map<String, Object> out = base("network-exposure", ok, status);
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("summary", Map.of("publishedPorts", rows.size(), "warning", rows.size()));
        return out;
    }

    /** Restart policy and healthcheck coverage from live container inspect data. */
    public Map<String, Object> restartPolicyAudit() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        List<Map<String, Object>> rows = new ArrayList<>();
        int missingHealthcheck = 0;
        int noRestartPolicy = 0;
        if (ok) {
            for (Map<String, Object> c : socket.containers()) {
                String id = str(c.get("Id"));
                if (id.isBlank()) continue;
                Map<String, Object> inspect = socket.object("/containers/" + id + "/json");
                Map hostConfig = asMap(inspect.get("HostConfig"));
                Map restartPolicy = asMap(hostConfig.get("RestartPolicy"));
                Map config = asMap(inspect.get("Config"));
                Map healthcheck = asMap(config.get("Healthcheck"));
                String policy = firstNonBlank(str(restartPolicy.get("Name")), "no");
                boolean hasHealthcheck = !healthcheck.isEmpty();
                if (!hasHealthcheck) missingHealthcheck++;
                if (policy.isBlank() || "no".equalsIgnoreCase(policy)) noRestartPolicy++;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("name", firstContainerName(c));
                row.put("image", str(c.get("Image")));
                row.put("state", str(c.get("State")));
                row.put("restartPolicy", policy);
                row.put("restartMaximumRetryCount", restartPolicy.getOrDefault("MaximumRetryCount", 0));
                row.put("healthcheck", hasHealthcheck ? "CONFIGURED" : "MISSING");
                row.put("status", (!hasHealthcheck || "no".equalsIgnoreCase(policy)) ? "WARN" : "OK");
                row.put("source", "docker-engine-api");
                rows.add(row);
            }
        }
        Map<String, Object> out = base("restart-policy-audit", ok, status);
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("summary", Map.of("containers", rows.size(), "missingHealthcheck", missingHealthcheck, "noRestartPolicy", noRestartPolicy));
        return out;
    }

    /** Environment risk audit. Values are never returned; secret-like variables are masked. */
    public Map<String, Object> environmentRiskAudit() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (ok) {
            for (Map<String, Object> c : socket.containers()) {
                String id = str(c.get("Id"));
                if (id.isBlank()) continue;
                Map<String, Object> inspect = socket.object("/containers/" + id + "/json");
                Map config = asMap(inspect.get("Config"));
                Object env = config.get("Env");
                if (!(env instanceof List<?> values)) continue;
                for (Object raw : values) {
                    String entry = str(raw);
                    int idx = entry.indexOf('=');
                    String key = idx > 0 ? entry.substring(0, idx) : entry;
                    if (!sensitiveEnvKey(key)) continue;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", id + ":" + key);
                    row.put("containerId", id);
                    row.put("container", firstContainerName(c));
                    row.put("image", str(c.get("Image")));
                    row.put("key", key);
                    row.put("value", "***MASKED***");
                    row.put("severity", "WARN");
                    row.put("message", "Secret-like environment variable detected; value is intentionally masked by the gateway.");
                    row.put("source", "docker-engine-api");
                    rows.add(row);
                }
            }
        }
        Map<String, Object> out = base("environment-risk-audit", ok, status);
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("summary", Map.of("secretLikeEnvironmentVariables", rows.size(), "valuesMasked", true));
        return out;
    }

    /** Mount risk audit from live container inspect data. */
    public Map<String, Object> mountRiskAudit() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (ok) {
            for (Map<String, Object> c : socket.containers()) {
                String id = str(c.get("Id"));
                if (id.isBlank()) continue;
                Map<String, Object> inspect = socket.object("/containers/" + id + "/json");
                Object mounts = inspect.get("Mounts");
                if (!(mounts instanceof List<?> mountRows)) continue;
                for (Object mo : mountRows) {
                    if (!(mo instanceof Map<?,?> m)) continue;
                    String source = str(m.get("Source"));
                    String destination = str(m.get("Destination"));
                    boolean dockerSocket = source.contains("docker.sock") || destination.contains("docker.sock");
                    boolean hostRoot = "/".equals(source) || "/".equals(destination);
                    boolean bind = "bind".equalsIgnoreCase(str(m.get("Type")));
                    if (!dockerSocket && !hostRoot && !bind) continue;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", id + ":" + destination);
                    row.put("containerId", id);
                    row.put("container", firstContainerName(c));
                    row.put("image", str(c.get("Image")));
                    row.put("type", str(m.get("Type")));
                    row.put("source", source);
                    row.put("destination", destination);
                    row.put("mode", str(m.get("Mode")));
                    row.put("rw", m.get("RW"));
                    row.put("severity", dockerSocket || hostRoot ? "ERROR" : "WARN");
                    row.put("message", dockerSocket ? "Docker socket mount detected" : hostRoot ? "Host root mount detected" : "Bind mount detected");
                    row.put("dataSource", "docker-engine-api");
                    rows.add(row);
                }
            }
        }
        Map<String, Object> out = base("mount-risk-audit", ok, status);
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("summary", riskSummary(rows));
        return out;
    }



    /** Container security posture from live container inspect data. */
    public Map<String, Object> containerSecurityAudit() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (ok) {
            for (Map<String, Object> c : socket.containers()) {
                String id = str(c.get("Id"));
                if (id.isBlank()) continue;
                Map<String, Object> inspect = socket.object("/containers/" + id + "/json");
                Map hostConfig = asMap(inspect.get("HostConfig"));
                Map config = asMap(inspect.get("Config"));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("name", firstContainerName(c));
                row.put("image", str(c.get("Image")));
                row.put("state", str(c.get("State")));
                row.put("privileged", Boolean.TRUE.equals(hostConfig.get("Privileged")));
                row.put("readonlyRootfs", Boolean.TRUE.equals(hostConfig.get("ReadonlyRootfs")));
                row.put("networkMode", str(hostConfig.get("NetworkMode")));
                row.put("pidMode", str(hostConfig.get("PidMode")));
                row.put("ipcMode", str(hostConfig.get("IpcMode")));
                row.put("user", firstNonBlank(str(config.get("User")), "root"));
                row.put("capAdd", hostConfig.getOrDefault("CapAdd", List.of()));
                row.put("capDrop", hostConfig.getOrDefault("CapDrop", List.of()));
                row.put("securityOpt", hostConfig.getOrDefault("SecurityOpt", List.of()));
                row.put("source", "docker-engine-api");
                List<String> findings = new ArrayList<>();
                if (Boolean.TRUE.equals(hostConfig.get("Privileged"))) findings.add("privileged container");
                if (!Boolean.TRUE.equals(hostConfig.get("ReadonlyRootfs"))) findings.add("root filesystem is writable");
                if (str(hostConfig.get("NetworkMode")).equalsIgnoreCase("host")) findings.add("host network mode");
                if (!str(hostConfig.get("PidMode")).isBlank() && str(hostConfig.get("PidMode")).equalsIgnoreCase("host")) findings.add("host PID namespace");
                if (!str(hostConfig.get("IpcMode")).isBlank() && str(hostConfig.get("IpcMode")).equalsIgnoreCase("host")) findings.add("host IPC namespace");
                if (firstNonBlank(str(config.get("User")), "root").equals("root")) findings.add("runs as root user");
                Object capAdd = hostConfig.get("CapAdd");
                if (capAdd instanceof List<?> list && !list.isEmpty()) findings.add("additional Linux capabilities configured");
                row.put("findings", findings);
                row.put("severity", findings.stream().anyMatch(f -> f.contains("privileged") || f.contains("host ")) ? "ERROR" : findings.isEmpty() ? "OK" : "WARN");
                row.put("message", findings.isEmpty() ? "No high-risk container security flags detected by Docker inspect." : String.join(", ", findings));
                rows.add(row);
            }
        }
        Map<String, Object> out = base("container-security-audit", ok, status);
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("summary", riskSummary(rows));
        return out;
    }

    /** Image hygiene audit from live image and container references. */
    public Map<String, Object> imageHygieneAudit() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (ok) {
            Set<String> usedImageIds = new LinkedHashSet<>();
            Set<String> usedImageRefs = new LinkedHashSet<>();
            for (Map<String, Object> c : socket.containers()) {
                usedImageRefs.add(str(c.get("Image")));
                String imageId = str(c.get("ImageID"));
                if (!imageId.isBlank()) usedImageIds.add(imageId.replace("sha256:", ""));
            }
            for (Map<String, Object> image : socket.images()) {
                String id = str(image.get("Id"));
                Object tagsObj = image.get("RepoTags");
                List<String> tags = new ArrayList<>();
                if (tagsObj instanceof List<?> list) for (Object tag : list) tags.add(str(tag));
                boolean dangling = tags.isEmpty() || tags.stream().allMatch(t -> t.contains("<none>"));
                boolean latestOnly = !tags.isEmpty() && tags.stream().allMatch(t -> t.endsWith(":latest"));
                boolean used = usedImageIds.contains(id.replace("sha256:", "")) || tags.stream().anyMatch(usedImageRefs::contains);
                List<String> findings = new ArrayList<>();
                if (dangling) findings.add("dangling or untagged image");
                if (latestOnly) findings.add("only latest tag detected");
                if (!used) findings.add("not referenced by current containers");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("shortId", shortId(id.replace("sha256:", "")));
                row.put("repoTags", tags);
                row.put("sizeBytes", image.getOrDefault("Size", 0));
                row.put("created", image.get("Created"));
                row.put("usedByContainer", used);
                row.put("findings", findings);
                row.put("severity", dangling ? "WARN" : latestOnly || !used ? "WARN" : "OK");
                row.put("source", "docker-engine-api");
                rows.add(row);
            }
        }
        Map<String, Object> out = base("image-hygiene-audit", ok, status);
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("summary", Map.of(
            "images", rows.size(),
            "warnings", rows.stream().filter(r -> "WARN".equals(r.get("severity"))).count(),
            "errors", rows.stream().filter(r -> "ERROR".equals(r.get("severity"))).count()
        ));
        return out;
    }

    /** Docker network inventory enriched with attached container counts. */
    public Map<String, Object> networkInventory() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (ok) {
            Map<String, Integer> attachments = new HashMap<>();
            for (Map<String, Object> c : socket.containers()) {
                for (String network : networksOf(c)) attachments.put(network, attachments.getOrDefault(network, 0) + 1);
            }
            for (Map<String, Object> n : socket.networks()) {
                String id = str(n.get("Id"));
                String name = str(n.get("Name"));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("name", name);
                row.put("driver", str(n.get("Driver")));
                row.put("scope", str(n.get("Scope")));
                row.put("internal", n.get("Internal"));
                row.put("attachable", n.get("Attachable"));
                row.put("containers", attachments.getOrDefault(name, 0));
                row.put("created", n.get("Created"));
                row.put("status", attachments.getOrDefault(name, 0) > 0 ? "ATTACHED" : "UNUSED");
                row.put("source", "docker-engine-api");
                rows.add(row);
            }
        }
        Map<String, Object> out = base("network-inventory", ok, status);
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("summary", Map.of("networks", rows.size(), "unused", rows.stream().filter(r -> "UNUSED".equals(r.get("status"))).count()));
        return out;
    }

    /** Preview of removable candidates derived from live Docker state. No delete/prune is executed. */
    public Map<String, Object> unusedResourceCandidates() {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok"));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (ok) {
            for (Map<String, Object> c : socket.containers()) {
                String state = str(c.get("State"));
                if ("exited".equalsIgnoreCase(state) || "dead".equalsIgnoreCase(state) || "created".equalsIgnoreCase(state)) {
                    rows.add(candidate("container", str(c.get("Id")), firstContainerName(c), state, c));
                }
            }
            for (Map<String, Object> image : socket.images()) {
                if (isDanglingImage(image)) rows.add(candidate("image", str(image.get("Id")), str(image.get("RepoTags")), "dangling", image));
            }
            for (Map<String, Object> volume : socket.volumes()) {
                Object usage = volume.get("UsageData");
                long refCount = usage instanceof Map<?,?> m ? longValue(m.get("RefCount")) : -1L;
                if (refCount == 0) rows.add(candidate("volume", str(volume.get("Name")), str(volume.get("Name")), "unused", volume));
            }
            for (Map<String, Object> network : itemsFrom(networkInventory().get("items"))) {
                if ("UNUSED".equals(network.get("status")) && !List.of("bridge", "host", "none").contains(str(network.get("name")))) {
                    rows.add(candidate("network", str(network.get("id")), str(network.get("name")), "unused", network));
                }
            }
        }
        Map<String, Object> out = base("unused-resource-candidates", ok, status);
        out.put("items", rows);
        out.put("count", rows.size());
        out.put("summary", Map.of("candidates", rows.size(), "previewOnly", true));
        out.put("note", "Preview only: this endpoint never removes containers, images, volumes or networks.");
        return out;
    }

    /** Backward-compatible build view now backed by live Docker build cache. */
    public Map<String, Object> builds() { return buildCache(); }
    public Map<String, Object> scout()  { return Map.of("live", Boolean.TRUE.equals(status().get("ok")), "items", List.of(), "toolStatus", status(), "realDataOnly", true, "state", "NOT_CONFIGURED"); }
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

    private Map<String, Object> liveObject(String resource, Map<String, Object> data) {
        Map<String, Object> status = status();
        boolean ok = Boolean.TRUE.equals(status.get("ok")) && !data.containsKey("error");
        Map<String, Object> out = base(resource, ok, status);
        out.put("data", data);
        out.put("items", List.of(data));
        out.put("count", ok ? 1 : 0);
        if (data.containsKey("error")) out.put("error", data.get("error"));
        return out;
    }

    private Map<String, Object> base(String resource, boolean live, Map<String, Object> status) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", live);
        out.put("tool", "docker-socket");
        out.put("resource", resource);
        out.put("toolStatus", status);
        out.put("realDataOnly", true);
        return out;
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

    private Map<String, Object> newProject(String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", name);
        row.put("name", name);
        row.put("services", new ArrayList<String>());
        row.put("containers", new ArrayList<Map<String, Object>>());
        row.put("images", new ArrayList<String>());
        row.put("networks", new ArrayList<String>());
        row.put("running", 0);
        row.put("exited", 0);
        row.put("unhealthy", 0);
        row.put("source", "docker-labels");
        return row;
    }

    private Map<String, Object> projectContainerRow(Map<String, Object> c, String service) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", str(c.get("Id")));
        row.put("name", firstContainerName(c));
        row.put("service", service);
        row.put("image", str(c.get("Image")));
        row.put("state", str(c.get("State")));
        row.put("status", str(c.get("Status")));
        row.put("ports", c.get("Ports"));
        row.put("networks", networksOf(c));
        return row;
    }

    private String projectStatus(Map<String, Object> project) {
        int unhealthy = ((Number) project.get("unhealthy")).intValue();
        int running = ((Number) project.get("running")).intValue();
        int total = ((List) project.get("containers")).size();
        if (unhealthy > 0) return "DEGRADED";
        if (total > 0 && running == total) return "RUNNING";
        if (running > 0) return "PARTIAL";
        return total == 0 ? "EMPTY" : "STOPPED";
    }

    private Map<String, Object> check(String name, String status, String message, Object details) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("status", status);
        row.put("message", message);
        row.put("details", details);
        row.put("source", "docker-engine-api");
        return row;
    }

    private void addDfSection(List<Map<String, Object>> items, String section, Object raw) {
        if (!(raw instanceof List<?> rows)) return;
        for (Object o : rows) {
            if (!(o instanceof Map<?,?> m)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("section", section);
            row.put("id", str(m.get("ID")));
            row.put("name", firstNonBlank(str(m.get("Name")), str(m.get("RepoTags")), str(m.get("Type")), str(m.get("ID"))));
            row.put("size", m.get("Size"));
            row.put("reclaimable", m.get("Reclaimable"));
            row.put("raw", m);
            items.add(row);
        }
    }

    private void addNode(List<Map<String, Object>> nodes, Set<String> ids, String id, String label, String type, String status) {
        if (!ids.add(id)) return;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", label);
        row.put("type", type);
        row.put("status", status);
        nodes.add(row);
    }

    private void addEdge(List<Map<String, Object>> edges, String from, String to, String relation) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("from", from);
        row.put("to", to);
        row.put("relation", relation);
        edges.add(row);
    }

    private String firstContainerName(Map<String, Object> raw) {
        Object names = raw.get("Names");
        if (names instanceof List list && !list.isEmpty()) return String.valueOf(list.get(0)).replaceFirst("^/+", "");
        String id = String.valueOf(raw.getOrDefault("Id", ""));
        return id.length() > 12 ? id.substring(0, 12) : id;
    }

    private String serviceFromName(String name) {
        String value = name == null ? "container" : name;
        value = value.replaceFirst("^nebulaops-v\\d+-\\d+-", "");
        value = value.replaceFirst("-\\d+$", "");
        return value.isBlank() ? "container" : value;
    }

    private Map labels(Map<String, Object> c) {
        Object labels = c.get("Labels");
        return labels instanceof Map ? (Map) labels : Collections.emptyMap();
    }

    private List<String> networksOf(Map<String, Object> c) {
        List<String> result = new ArrayList<>();
        try {
            Object ns = c.get("NetworkSettings");
            if (ns instanceof Map<?,?> networkSettings) {
                Object nets = networkSettings.get("Networks");
                if (nets instanceof Map<?,?> map) {
                    for (Object key : map.keySet()) if (key != null) result.add(String.valueOf(key));
                }
            }
        } catch (Exception ignored) { }
        return result;
    }

    private List<String> mountsOf(Map<String, Object> c) {
        List<String> result = new ArrayList<>();
        Object mounts = c.get("Mounts");
        if (mounts instanceof List<?> rows) {
            for (Object o : rows) {
                if (o instanceof Map<?,?> m) {
                    String name = firstNonBlank(str(m.get("Name")), str(m.get("Source")), str(m.get("Destination")));
                    if (!name.isBlank()) result.add(name);
                }
            }
        }
        return result;
    }

    private void addUnique(List<String> target, String value) {
        if (value == null || value.isBlank()) return;
        if (!target.contains(value)) target.add(value);
    }

    private void addUnique(List<String> target, List<String> values) {
        for (String value : values) addUnique(target, value);
    }


    private Map<String, Object> candidate(String type, String id, String name, String status, Object raw) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("type", type);
        row.put("name", name);
        row.put("status", status);
        row.put("source", "docker-engine-api");
        row.put("raw", raw);
        return row;
    }

    private boolean isDanglingImage(Map<String, Object> image) {
        Object tags = image.get("RepoTags");
        if (!(tags instanceof List<?> list) || list.isEmpty()) return true;
        return list.stream().allMatch(v -> String.valueOf(v).contains("<none>"));
    }


    private boolean sensitiveEnvKey(String key) {
        String k = key == null ? "" : key.toUpperCase(Locale.ROOT);
        return k.contains("PASSWORD") || k.contains("PASSWD") || k.contains("TOKEN") || k.contains("SECRET")
            || k.contains("PRIVATE_KEY") || k.contains("CREDENTIAL") || k.contains("AUTH") || k.endsWith("_KEY");
    }

    private String shortId(String id) { return id == null || id.length() <= 12 ? str(id) : id.substring(0, 12); }
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> itemsFrom(Object value) {
        if (value instanceof List<?> rows) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object row : rows) if (row instanceof Map<?,?> m) out.add((Map<String, Object>) m);
            return out;
        }
        return List.of();
    }

    private List<String> publicPortsOf(Map<String, Object> c) {
        List<String> result = new ArrayList<>();
        Object ports = c.get("Ports");
        if (ports instanceof List<?> rows) {
            for (Object o : rows) {
                if (o instanceof Map<?,?> p) {
                    Object publicPort = p.get("PublicPort");
                    if (publicPort != null && !String.valueOf(publicPort).isBlank() && !"0".equals(String.valueOf(publicPort))) {
                        result.add(String.valueOf(publicPort));
                    }
                }
            }
        }
        return result;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    private Map<String, Object> pressureSummary(List<Map<String, Object>> rows) {
        long critical = rows.stream().filter(r -> "CRITICAL".equals(r.get("pressure"))).count();
        long high = rows.stream().filter(r -> "HIGH".equals(r.get("pressure"))).count();
        double maxCpu = rows.stream().mapToDouble(r -> doubleValue(r.get("cpuPercent"))).max().orElse(0.0);
        double maxMemory = rows.stream().mapToDouble(r -> doubleValue(r.get("memoryPercent"))).max().orElse(0.0);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runningContainers", rows.size());
        summary.put("critical", critical);
        summary.put("high", high);
        summary.put("maxCpuPercent", Math.round(maxCpu * 100.0) / 100.0);
        summary.put("maxMemoryPercent", Math.round(maxMemory * 100.0) / 100.0);
        return summary;
    }

    private Map<String, Object> riskSummary(List<Map<String, Object>> rows) {
        long errors = rows.stream().filter(r -> "ERROR".equals(r.get("severity"))).count();
        long warnings = rows.stream().filter(r -> "WARN".equals(r.get("severity"))).count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("risks", rows.size());
        summary.put("errors", errors);
        summary.put("warnings", warnings);
        return summary;
    }

    private String pressureLevel(Map<String, Object> row) {
        double cpu = doubleValue(row.get("cpuPercent"));
        double mem = doubleValue(row.get("memoryPercent"));
        if (cpu >= 90.0 || mem >= 90.0) return "CRITICAL";
        if (cpu >= 75.0 || mem >= 75.0) return "HIGH";
        if (cpu >= 50.0 || mem >= 50.0) return "MEDIUM";
        return "NORMAL";
    }

    private double memoryPercent(Map<String, Object> row) {
        long usage = longValue(row.get("memoryUsageBytes"));
        long limit = longValue(row.get("memoryLimitBytes"));
        if (usage <= 0 || limit <= 0) return 0.0;
        return Math.round(((double) usage / (double) limit) * 10000.0) / 100.0;
    }

    private long longValue(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(value)); } catch (Exception ignored) { return 0L; }
    }

    private int intValue(Object value) {
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return 0; }
    }

    private double doubleValue(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception ignored) { return 0.0; }
    }


    @SuppressWarnings("unchecked")
    private Map asMap(Object value) {
        return value instanceof Map<?,?> m ? (Map) m : Collections.emptyMap();
    }

    private String str(Object o)   { return o == null ? "" : String.valueOf(o); }

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
