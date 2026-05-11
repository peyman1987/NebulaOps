package dev.nebulaops.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.nebulaops.gateway.client.ToolCommandClient;
import dev.nebulaops.gateway.client.ToolResult;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * NebulaOps v23.4 — live diagnostics bundle exporter.
 *
 * The bundle is assembled only from runtime sources available at request time:
 * Docker Engine, docker CLI logs, kubectl, Helm, gateway runtime state, extension
 * status, frontend remote verification and the static preflight script. The service
 * never adds seeded issue rows, demo datasets or fallback inventories.
 */
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class RuntimeDiagnosticsBundleService {
    private static final int COMMAND_TIMEOUT_SECONDS = 90;
    private static final int LOG_TAIL_LINES = 220;
    private static final int MAX_CONTAINER_LOG_FILES = 60;

    private final DockerRuntimeService docker;
    private final KubernetesPlatformService kubernetes;
    private final ToolCommandClient tools;
    private final ObjectMapper mapper;

    public RuntimeDiagnosticsBundleService(DockerRuntimeService docker,
                                           KubernetesPlatformService kubernetes,
                                           ToolCommandClient tools,
                                           ObjectMapper mapper) {
        this.docker = docker;
        this.kubernetes = kubernetes;
        this.tools = tools;
        this.mapper = mapper;
    }

    public Map<String, Object> bundle() {
        BundleAssembly assembly = assemble();
        Map<String, Object> out = manifest(assembly);
        out.put("files", filePayloads(assembly.files));
        out.put("message", "Diagnostics bundle generated from live runtime sources only. Download the ZIP from /api/runtime/diagnostics/bundle.zip for support handoff.");
        return out;
    }

    public byte[] bundleZip() {
        try {
            BundleAssembly assembly = assemble();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
                writeJson(zip, "manifest.json", manifest(assembly));
                for (BundleFile file : assembly.files) {
                    if (file.content instanceof String text) {
                        writeText(zip, file.path, text);
                    } else {
                        writeJson(zip, file.path, file.content);
                    }
                }
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build live diagnostics bundle: " + e.getMessage(), e);
        }
    }

    private BundleAssembly assemble() {
        String generatedAt = Instant.now().toString();
        List<BundleFile> files = new ArrayList<>();

        addJson(files, "gateway/health.json", gatewayHealth(generatedAt));
        addJson(files, "docker/status.json", docker.status());
        addJson(files, "docker/containers.json", docker.containers());
        addJson(files, "docker/images.json", docker.images());
        addJson(files, "docker/networks.json", docker.networks());
        addJson(files, "docker/events.json", docker.events());
        addJson(files, "docker/compose-projects.json", docker.projects());
        addJson(files, "docker/diagnostics.json", docker.diagnostics());
        addContainerLogs(files);

        addJson(files, "kubernetes/pods.json", kubernetes.resource("pods", "all"));
        addJson(files, "kubernetes/events.json", kubernetes.events("all"));
        addJson(files, "kubernetes/deployments.json", kubernetes.resource("deployments", "all"));
        addJson(files, "kubernetes/services.json", kubernetes.resource("services", "all"));
        addJson(files, "kubernetes/ingress.json", kubernetes.resource("ingress", "all"));
        addKubectlDescribe(files, "pods", "kubectl describe pods -A");
        addKubectlDescribe(files, "deployments", "kubectl describe deployments -A");
        addKubectlDescribe(files, "services", "kubectl describe services -A");
        addKubectlDescribe(files, "ingress", "kubectl describe ingress -A");

        addJson(files, "helm/releases.json", kubernetes.helmReleases("all"));
        addJson(files, "extensions/status.json", extensionStatusFromDocker());
        addCommand(files, "frontend/remote-verification.txt", "cd /workspace && node frontend/tools/verify-remotes.mjs", COMMAND_TIMEOUT_SECONDS);
        addCommand(files, "preflight/preflight.txt", safePreflightCommand(), COMMAND_TIMEOUT_SECONDS);

        return new BundleAssembly(generatedAt, files);
    }

    private void addContainerLogs(List<BundleFile> files) {
        Map<String, Object> containers = docker.containers();
        Object raw = containers.get("items");
        if (!(raw instanceof List<?> list) || !Boolean.TRUE.equals(containers.get("live"))) {
            addJson(files, "docker/container-logs/unavailable.json", Map.of(
                    "live", false,
                    "realDataOnly", true,
                    "message", "Docker containers were unavailable, so container log tail files were not generated.",
                    "toolStatus", containers.getOrDefault("toolStatus", Map.of())
            ));
            return;
        }
        int index = 0;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> container)) continue;
            if (index >= MAX_CONTAINER_LOG_FILES) break;
            String id = str(container.get("Id"));
            if (id.isBlank()) continue;
            String name = firstName(container.get("Names"));
            String safeName = safeFile(firstNonBlank(name, shortId(id), "container-" + index));
            String command = "docker logs --tail " + LOG_TAIL_LINES + " --timestamps " + q(id);
            addCommand(files, "docker/container-logs/" + safeName + ".log", command, 20);
            index++;
        }
        addJson(files, "docker/container-logs/index.json", Map.of(
                "live", true,
                "realDataOnly", true,
                "tailLines", LOG_TAIL_LINES,
                "generatedLogFiles", index,
                "maxContainerLogFiles", MAX_CONTAINER_LOG_FILES,
                "message", "Container log files are generated from docker logs at request time."
        ));
    }

    private void addKubectlDescribe(List<BundleFile> files, String resource, String command) {
        ToolResult result = kubernetes.runKubectl(command, null, COMMAND_TIMEOUT_SECONDS);
        String text = textResult(command, result);
        addText(files, "kubernetes/describe/" + safeFile(resource) + ".txt", text, result.ok(), command, result);
    }

    private void addCommand(List<BundleFile> files, String path, String command, int timeoutSeconds) {
        ToolResult result = tools.shell(command, timeoutSeconds);
        addText(files, path, textResult(command, result), result.ok(), command, result);
    }

    private String textResult(String command, ToolResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("# command\n").append(command).append("\n\n");
        sb.append("# status\n");
        sb.append("ok=").append(result.ok()).append("\n");
        sb.append("exitCode=").append(result.exitCode()).append("\n");
        sb.append("message=").append(result.message()).append("\n");
        sb.append("durationMs=").append(result.durationMs()).append("\n");
        sb.append("executedAt=").append(result.executedAt()).append("\n\n");
        sb.append("# stdout\n").append(nullToEmpty(result.stdout())).append("\n\n");
        sb.append("# stderr\n").append(nullToEmpty(result.stderr())).append("\n");
        return sb.toString();
    }


    private String safePreflightCommand() {
        return "cd /workspace && ("
                + "python3 scripts/validate-package.py && "
                + "python3 scripts/verify-live-only-runtime.py && "
                + "python3 scripts/validate-yaml.py docker-compose.yml infrastructure/docker-compose.yml .gitlab-ci.yml infrastructure/argocd/application.yaml infrastructure/argocd/project.yaml infrastructure/argocd/applicationset.yaml infrastructure/kubernetes/apiforge.yaml extensions/*/k8s/deployment.yml && "
                + "node frontend/tools/verify-remotes.mjs && "
                + "bash scripts/wsl/ensure-frontend-dist.sh"
                + ")";
    }

    private Map<String, Object> gatewayHealth(String generatedAt) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", true);
        out.put("realDataOnly", true);
        out.put("state", "UP");
        out.put("service", "gateway-service");
        out.put("generatedAt", generatedAt);
        out.put("java", System.getProperty("java.version"));
        out.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        out.put("freeMemoryBytes", Runtime.getRuntime().freeMemory());
        out.put("totalMemoryBytes", Runtime.getRuntime().totalMemory());
        out.put("maxMemoryBytes", Runtime.getRuntime().maxMemory());
        out.put("message", "Gateway generated this health section while handling the diagnostics bundle request.");
        return out;
    }

    private Map<String, Object> extensionStatusFromDocker() {
        Map<String, Object> containers = docker.containers();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "docker-runtime");
        out.put("executedAt", Instant.now().toString());
        out.put("realDataOnly", true);
        out.put("live", Boolean.TRUE.equals(containers.get("live")));
        out.put("toolStatus", containers.get("toolStatus"));
        List<Map<String, Object>> items = new ArrayList<>();
        Object raw = containers.get("items");
        if (Boolean.TRUE.equals(containers.get("live")) && raw instanceof List<?> list) {
            for (Object value : list) {
                if (!(value instanceof Map<?, ?> map)) continue;
                String name = firstName(map.get("Names"));
                String image = str(map.get("Image"));
                String labels = str(map.get("Labels"));
                String probe = (name + " " + image + " " + labels).toLowerCase(Locale.ROOT);
                if (!probe.contains("mfe-") && !probe.contains("nebulaops-mfe")) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", shortId(str(map.get("Id"))));
                row.put("name", name);
                row.put("image", image);
                row.put("status", map.get("Status"));
                row.put("state", map.get("State"));
                row.put("ports", map.get("Ports"));
                row.put("created", map.get("Created"));
                row.put("live", "running".equalsIgnoreCase(str(map.get("State"))));
                items.add(row);
            }
        }
        out.put("items", items);
        out.put("count", items.size());
        if (!Boolean.TRUE.equals(out.get("live"))) {
            out.put("state", "EXTENSION_DISCOVERY_DOCKER_UNAVAILABLE");
            out.put("message", "Extension status requires live Docker runtime data.");
        } else if (items.isEmpty()) {
            out.put("state", "NO_EXTENSION_CONTAINERS_FOUND");
            out.put("message", "Docker is reachable but no NebulaOps MFE extension containers were returned by Docker Engine.");
        }
        return out;
    }

    private Map<String, Object> manifest(BundleAssembly assembly) {
        List<Map<String, Object>> entries = new ArrayList<>();
        int ok = 0;
        int failed = 0;
        for (BundleFile file : assembly.files) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", file.path);
            row.put("type", file.type);
            row.put("ok", file.ok);
            if (file.command != null && !file.command.isBlank()) row.put("command", file.command);
            if (file.toolStatus != null) row.put("toolStatus", file.toolStatus.asMap());
            entries.add(row);
            if (file.ok) ok++; else failed++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", "nebulaops-v23.4-diagnostics-bundle");
        out.put("version", "23.4.0");
        out.put("live", true);
        out.put("realDataOnly", true);
        out.put("generatedAt", assembly.generatedAt);
        out.put("zipEndpoint", "/api/runtime/diagnostics/bundle.zip");
        out.put("jsonEndpoint", "/api/runtime/diagnostics/bundle");
        out.put("summary", Map.of("files", assembly.files.size(), "ok", ok, "failedOrUnavailable", failed));
        out.put("entries", entries);
        out.put("contents", List.of(
                "Docker containers, images, networks, events, compose project status and container log tails",
                "Kubernetes pods, events and describe output for pods/deployments/services/ingress",
                "Helm releases",
                "Gateway health generated during the request",
                "Extension status derived from live Docker runtime",
                "Frontend remote verification output",
                "Preflight output"
        ));
        return out;
    }


    private List<Map<String, Object>> filePayloads(List<BundleFile> files) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BundleFile file : files) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", file.path);
            row.put("type", file.type);
            row.put("ok", file.ok);
            if (file.command != null && !file.command.isBlank()) row.put("command", file.command);
            if (file.toolStatus != null) row.put("toolStatus", file.toolStatus.asMap());
            row.put("content", file.content);
            rows.add(row);
        }
        return rows;
    }

    private void addJson(List<BundleFile> files, String path, Object content) {
        boolean ok = isLiveOk(content);
        files.add(new BundleFile(path, "application/json", ok, content, null, null));
    }

    private void addText(List<BundleFile> files, String path, String content, boolean ok, String command, ToolResult status) {
        files.add(new BundleFile(path, "text/plain", ok, content, command, status));
    }

    private boolean isLiveOk(Object content) {
        if (!(content instanceof Map<?, ?> map)) return true;
        Object ok = map.get("ok");
        if (ok instanceof Boolean b) return b;
        Object live = map.get("live");
        if (live instanceof Boolean b) return b;
        Object toolStatus = map.get("toolStatus");
        if (toolStatus instanceof Map<?, ?> ts && ts.get("ok") instanceof Boolean b) return b;
        return !map.containsKey("error");
    }

    private void writeJson(ZipOutputStream zip, String path, Object value) throws Exception {
        writeBytes(zip, path, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
    }

    private void writeText(ZipOutputStream zip, String path, String value) throws Exception {
        writeBytes(zip, path, nullToEmpty(value).getBytes(StandardCharsets.UTF_8));
    }

    private void writeBytes(ZipOutputStream zip, String path, byte[] bytes) throws Exception {
        ZipEntry entry = new ZipEntry(path);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private String firstName(Object names) {
        if (names instanceof List<?> list && !list.isEmpty()) return str(list.get(0)).replaceFirst("^/+", "");
        return "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    private String safeFile(String value) {
        String safe = str(value).replaceAll("[^A-Za-z0-9_.@-]", "-").replaceAll("-+", "-");
        return safe.isBlank() ? "resource" : safe;
    }

    private String shortId(String id) {
        return id != null && id.length() > 12 ? id.substring(0, 12) : str(id);
    }

    private String str(Object value) { return value == null ? "" : String.valueOf(value); }
    private String nullToEmpty(String value) { return value == null ? "" : value; }
    private String q(String value) { return "'" + nullToEmpty(value).replace("'", "'\\''") + "'"; }

    private record BundleFile(String path, String type, boolean ok, Object content, String command, ToolResult toolStatus) {}
    private record BundleAssembly(String generatedAt, List<BundleFile> files) {}
}
