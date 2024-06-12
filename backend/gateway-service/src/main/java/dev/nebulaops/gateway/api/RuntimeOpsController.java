package dev.nebulaops.gateway.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/runtime")
public class RuntimeOpsController {
    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping("/docker/containers")
    public List<Map<String, Object>> containers() {
        String out = shell("docker ps -a --format '{{json .}}'");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : out.split("\\R")) {
            if (line.isBlank()) continue;
            try {
                rows.add(mapper.readValue(line, new TypeReference<>() {
                }));
            } catch (Exception e) {
                rows.add(Map.of("Names", line, "Status", "parse-warning"));
            }
        }
        return rows;
    }

    @GetMapping("/docker/images")
    public List<Map<String, Object>> images() {
        String out = shell("docker images --format '{{json .}}'");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : out.split("\\R")) {
            if (line.isBlank()) continue;
            try {
                rows.add(mapper.readValue(line, new TypeReference<>() {
                }));
            } catch (Exception ignored) {
            }
        }
        return rows;
    }

    @GetMapping("/docker/volumes")
    public List<Map<String, Object>> volumes() {
        String out = shell("docker volume ls --format '{{json .}}'");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : out.split("\\R")) {
            if (line.isBlank()) continue;
            try {
                rows.add(mapper.readValue(line, new TypeReference<>() {
                }));
            } catch (Exception ignored) {
            }
        }
        return rows;
    }

    @GetMapping("/docker/stats")
    public List<Map<String, Object>> stats() {
        String out = shell("docker stats --no-stream --format '{{json .}}'");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : out.split("\\R")) {
            if (line.isBlank()) continue;
            try {
                rows.add(mapper.readValue(line, new TypeReference<>() {
                }));
            } catch (Exception ignored) {
            }
        }
        return rows;
    }

    @PostMapping("/docker/containers/{id}/{action}")
    public Map<String, Object> containerAction(@PathVariable String id, @PathVariable String action) {
        Set<String> allowed = Set.of("start", "stop", "restart", "pause", "unpause", "kill", "rm");
        if (!allowed.contains(action))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported Docker action: " + action);
        String safeId = id.replaceAll("[^A-Za-z0-9_.:-]", "");
        String command = action.equals("rm") ? "docker rm -f " + safeId : "docker " + action + " " + safeId;
        String out = shell(command);
        return Map.of("id", safeId, "action", action, "output", out.trim(), "at", Instant.now().toString());
    }

    @GetMapping("/helm/releases")
    public List<Map<String, Object>> helmReleases(@RequestParam(defaultValue = "all") String namespace) {
        String ns = namespace.equals("all") ? "-A" : "-n " + safe(namespace);
        String out = shell("helm list " + ns + " -o json");
        try {
            return mapper.readValue(out, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cannot parse helm output: " + e.getMessage());
        }
    }

    @PostMapping("/helm/releases/{release}/uninstall")
    public Map<String, Object> uninstallRelease(@PathVariable String release, @RequestParam(defaultValue = "default") String namespace) {
        String out = shell("helm uninstall " + safe(release) + " -n " + safe(namespace));
        return Map.of("release", release, "namespace", namespace, "output", out.trim(), "at", Instant.now().toString());
    }

    @GetMapping("/grafana/health")
    public Map<String, Object> grafanaHealth() {
        String url = System.getenv().getOrDefault("GRAFANA_URL", "http://grafana:3000");
        String out = shell("curl -fsS " + url + "/api/health || curl -fsS http://localhost:3000/api/health");
        try {
            return mapper.readValue(out, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of("database", "unknown", "version", "unknown", "raw", out);
        }
    }

    @GetMapping("/grafana/dashboards")
    public List<Map<String, Object>> grafanaDashboards() {
        String url = System.getenv().getOrDefault("GRAFANA_URL", "http://grafana:3000");
        String auth = System.getenv().getOrDefault("GRAFANA_AUTH", "admin:admin");
        String out = shell("curl -fsS -u " + auth + " '" + url + "/api/search?type=dash-db' || true");
        try {
            return mapper.readValue(out, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of(Map.of("title", "Grafana unavailable", "message", out));
        }
    }

    @GetMapping("/terraform/validate")
    public Map<String, Object> terraformValidate() {
        String out = shell("cd infrastructure/terraform 2>/dev/null || cd /app/infrastructure/terraform 2>/dev/null || cd /workspace/infrastructure/terraform; terraform init -backend=false -input=false >/dev/null 2>&1 || true; terraform validate -no-color");
        return Map.of("status", "validated", "output", out.trim(), "at", Instant.now().toString());
    }

    @GetMapping("/terraform/plan")
    public Map<String, Object> terraformPlan(@RequestParam(defaultValue = "nebulaops-v18") String clusterName, @RequestParam(defaultValue = "nebulaops") String namespace) {
        String out = shell("cd infrastructure/terraform 2>/dev/null || cd /app/infrastructure/terraform 2>/dev/null || cd /workspace/infrastructure/terraform; terraform init -backend=false -input=false >/dev/null 2>&1 || true; terraform plan -no-color -input=false -var='cluster_name=" + safe(clusterName) + "' -var='namespace=" + safe(namespace) + "'");
        return Map.of("clusterName", clusterName, "namespace", namespace, "output", out.trim(), "at", Instant.now().toString());
    }

    private String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9_.:-]", "");
    }

    private String shell(String command) {
        try {
            Process process = new ProcessBuilder("bash", "-lc", command).redirectErrorStream(true).start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            if (!process.waitFor(35, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Command timed out");
            }
            if (process.exitValue() != 0) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, output);
            return output;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }
}
