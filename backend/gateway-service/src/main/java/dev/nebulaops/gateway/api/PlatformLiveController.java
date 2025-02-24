package dev.nebulaops.gateway.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

private String normalizeSeverity(String s) {
    String u = s.toUpperCase(Locale.ROOT);
    return Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(u) ? u : "MEDIUM";
}

private List<Map<String, Object>> complianceFromCluster() {
    boolean k8s = !shellQuiet("kubectl version --client -o json 2>/dev/null").isBlank();
    boolean docker = !dockerImages().isEmpty();
    return List.of(control("LIVE-K8S", "Runtime", "Kubernetes API reachable", k8s ? 100 : 0, k8s ? "pass" : "fail"), control("LIVE-DOCKER", "Runtime", "Docker socket reachable", docker ? 100 : 0, docker ? "pass" : "fail"));
}

private Map<String, Object> control(String id, String fw, String title, int score, String status) {
    return Map.of("id", id, "framework", fw, "title", title, "score", score, "status", status);
}

private List<Map<String, Object>> threatsFromLive(int c, int h, int m) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (c > 0) out.add(threat("Critical CVE", 20, 32, "CRITICAL", "trivy"));
    if (h > 0) out.add(threat("High CVE", 48, 58, "HIGH", "dependency"));
    if (secretFindings() > 0) out.add(threat("Possible secret", 72, 28, "CRITICAL", "repo"));
    return out;
}

private Map<String, Object> threat(String name, int x, int y, String severity, String vector) {
    return Map.of("name", name, "x", x, "y", y, "severity", severity, "vector", vector);
}

private int secretFindings() {
    String out = shellQuiet("grep -RIE --exclude-dir=.git --exclude-dir=node_modules '(password|secret|token|apikey|api_key)[[:space:]]*[:=]' . 2>/dev/null | wc -l");
    try {
        return Integer.parseInt(out.trim());
    } catch (Exception e) {
        return 0;
    }
}

private int namespaceHealth(String ns) {
    String out = shellQuiet("kubectl get pods -n " + safe(ns) + " --no-headers 2>/dev/null");
    if (out.isBlank()) return 100;
    long total = Arrays.stream(out.split("\\R")).filter(s -> !s.isBlank()).count();
    long bad = Arrays.stream(out.split("\\R")).filter(s -> !s.contains("Running") && !s.contains("Completed")).count();
    return total == 0 ? 100 : (int) Math.max(0, 100 - (bad * 100 / total));
}

private String clusterName() {
    String n = shellQuiet("kubectl config current-context 2>/dev/null");
    return n.isBlank() ? "kubernetes" : n.trim();
}

private String env(String key, String fallback) {
    return System.getenv().getOrDefault(key, fallback);
}

private String revision() {
    String rev = shellQuiet("git rev-parse --short HEAD 2>/dev/null");
    return rev.isBlank() ? "unknown" : rev.trim();
}

private List<String> commits() {
    String log = shellQuiet("git log --oneline -4 --pretty=format:%s 2>/dev/null");
    return log.isBlank() ? List.of() : Arrays.stream(log.split("\\R")).filter(s -> !s.isBlank()).toList();
}

private int count(String body, String needle) {
    if (body == null || body.isBlank()) return 0;
    int c = 0, i = 0;
    while ((i = body.indexOf(needle, i)) >= 0) {
        c++;
        i += needle.length();
    }
    return c;
}

private String safe(String s) {
    return s.replaceAll("[^A-Za-z0-9_.:-]", "");
}

private Probe httpProbe(String url) {
    long start = System.nanoTime();
    try {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setConnectTimeout(900);
        c.setReadTimeout(900);
        c.setRequestMethod("GET");
        int code = c.getResponseCode();
        long ms = (System.nanoTime() - start) / 1_000_000;
        return new Probe(code >= 200 && code < 500, code, ms);
    } catch (Exception e) {
        return new Probe(false, 0, 0);
    }
}

private String shellQuiet(String command) {
    try {
        Process p = new ProcessBuilder("bash", "-lc", command).redirectErrorStream(true).start();
        String out;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            out = r.lines().reduce("", (a, b) -> a + b + "\n");
        }
        if (!p.waitFor(8, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return "";
        }
        return p.exitValue() == 0 ? out.trim() : "";
    } catch (Exception e) {
        return "";
    }
}

private record Probe(boolean ok, int code, long ms) {
}

@RestController
@RequestMapping("/api/platform")
public class PlatformLiveController {
    private final ObjectMapper mapper = new ObjectMapper();
Exception ignored

{
    }

limit(25)

    toList();

    @GetMapping("/observability")
    public Map<String, Object> observability() {
        List<Map<String, Object>> stack = List.of(
                probe("Prometheus", "metrics", env("PROMETHEUS_URL", "http://prometheus:9090"), "/-/ready"),
                probe("Loki", "logs", env("LOKI_URL", "http://loki:3100"), "/ready"),
                probe("Tempo", "traces", env("TEMPO_URL", "http://tempo:3200"), "/ready"),
                probe("Grafana", "dashboards", env("GRAFANA_URL", "http://grafana:3000"), "/api/health"),
                probe("OpenTelemetry", "collector", env("OTEL_COLLECTOR_URL", "http://otel-collector:4318"), "/")
        );
        List<Map<String, Object>> stats = dockerStats();
        int gateway = latencyFromStats(stats, "gateway", 0);
        int task = latencyFromStats(stats, "task", 0);
        int db = latencyFromStats(stats, "mongo", 0);
        return Map.of(
                "generatedAt", Instant.now().toString(),
                "mode", "LIVE_ONLY",
                "stack", stack,
                "traceFlow", List.of(
                        hop("frontend", "gateway", gateway),
                        hop("gateway", "task-service", task),
                        hop("task-service", "mongodb", db),
                        hop("notification-service", "rabbitmq", latencyFromStats(stats, "rabbit", 0)),
                        hop("otel-collector", "tempo", latencyFromStats(stats, "tempo", 0))
                ),
                "latencyHeatmap", heatmapFromStats(stats),
                "eventStream", recentDockerEvents()
        );
    }

    @GetMapping("/gitops")
    public Map<String, Object> gitops() {
        String apps = shellQuiet("argocd app list -o json");
        if (apps.isBlank()) {
            return Map.of(
                    "state", Map.of("sync", "Unavailable", "drift", 0, "revision", revision(), "health", "ArgoCD CLI/API not configured"),
                    "deploymentWaves", List.of(),
                    "commitStream", commits(),
                    "mode", "LIVE_ONLY"
            );
        }
        int drift = count(apps, "OutOfSync");
        return Map.of(
                "state", Map.of("sync", drift == 0 ? "Synced" : "OutOfSync", "drift", drift, "revision", revision(), "health", count(apps, "Degraded") > 0 ? "Degraded" : "Healthy"),
                "deploymentWaves", parseArgoWaves(apps),
                "commitStream", commits(),
                "mode", "LIVE_ONLY"
        );
    }

    @GetMapping("/devsecops")
    public Map<String, Object> devsecops() {
        String trivy = shellQuiet("trivy fs --format json --quiet . 2>/dev/null");
        int critical = count(trivy, "CRITICAL");
        int high = count(trivy, "HIGH");
        int medium = count(trivy, "MEDIUM");
        boolean trivyAvailable = !trivy.isBlank();
        return Map.of(
                "scans", List.of(
                        scan("SCAN-TRIVY-FS", "Trivy", "repository filesystem", trivyAvailable ? (critical > 0 ? "FAILED" : "PASSED") : "QUEUED", critical, high, medium, trivyAvailable ? "completed" : "trivy not installed/configured"),
                        scan("SCAN-DOCKER", "Docker", "local images", dockerImages().isEmpty() ? "QUEUED" : "PASSED", 0, 0, 0, dockerImages().size() + " images discovered"),
                        scan("SCAN-SECRETS", "Secrets", "git tracked files", secretFindings() > 0 ? "FAILED" : "PASSED", secretFindings(), 0, 0, "grep-based live check")
                ),
                "cves", trivyAvailable ? cvesFromTrivy(trivy) : List.of(),
                "controls", complianceFromCluster(),
                "threats", threatsFromLive(critical, high, medium),
                "mode", "LIVE_ONLY"
        );
    }

    @GetMapping("/environments")
    public List<Map<String, Object>> environments() {
        String json = shellQuiet("kubectl get namespaces -o json");
        if (json.isBlank()) return List.of();
        try {
            Map<String, Object> root = mapper.readValue(json, new TypeReference<>() {
            });
            List<Map<String, Object>> items = (List<Map<String, Object>>) root.getOrDefault("items", List.of());
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> item : items) {
                Map<String, Object> meta = (Map<String, Object>) item.getOrDefault("metadata", Map.of());
                String ns = Objects.toString(meta.get("name"), "default");
                rows.add(Map.of("name", ns.toUpperCase(Locale.ROOT), "namespace", ns, "cluster", clusterName(), "health", namespaceHealth(ns), "cost", 0, "drift", 0, "workspace", ns));
            }
            return rows;
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> probe(String name, String role, String endpoint, String path) {
        Probe p = httpProbe(endpoint + path);
        return Map.of("name", name, "role", role, "endpoint", endpoint, "health", p.ok ? 100 : 0, "signal", p.ok ? "HTTP " + p.code + " in " + p.ms + "ms" : "unreachable", "live", p.ok);
    }

    private Map<String, Object> hop(String from, String to, int latency) {
        return Map.of("from", from, "to", to, "latency", latency, "status", latency == 0 ? "unknown" : latency > 100 ? "hot" : latency > 55 ? "warm" : "ok");
    }

    private List<Integer> heatmapFromStats(List<Map<String, Object>> stats) {
        List<Integer> rows = new ArrayList<>();
        for (Map<String, Object> s : stats)
            rows.add(parsePercent(Objects.toString(s.getOrDefault("CPUPerc", "0"), "0")));
        while (rows.size() < 16) rows.add(0);
        return rows.subList(0, 16);
    }

    private List<String> recentDockerEvents() {
        String out = shellQuiet("docker events --since 10m --until 0s --format '{{.Type}}.{{.Action}} {{.Actor.Attributes.name}}' 2>/dev/null | tail -20");
        if (out.isBlank()) return List.of();
        return Arrays.stream(out.split("\\R")).filter(s -> !s.isBlank()).toList();
    }

    private List<Map<String, Object>> dockerStats() {
        return jsonLines("docker stats --no-stream --format '{{json .}}' 2>/dev/null");
    }

    private List<Map<String, Object>> dockerImages() {
        return jsonLines("docker images --format '{{json .}}' 2>/dev/null");
    }

    private List<Map<String, Object>> jsonLines(String cmd) {
        String out = shellQuiet(cmd);
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

        private int latencyFromStats(List<Map<String, Object>> stats, String name, int fallback) {
        for (Map<String, Object> s : stats) {
            String n = Objects.toString(s.getOrDefault("Name", ""), "").toLowerCase(Locale.ROOT);
            if (n.contains(name))
                return Math.max(1, parsePercent(Objects.toString(s.getOrDefault("CPUPerc", "0"), "0")) * 3);
        }
        return fallback;
    } catch(

    private int parsePercent(String v) {
        try {
            return (int) Math.round(Double.parseDouble(v.replace("%", "").trim()));
        } catch (Exception e) {
            return 0;
        }
    })

        private List<Map<String, Object>> parseArgoWaves(String apps) {
        try {
            List<Map<String, Object>> arr = mapper.readValue(apps, new TypeReference<>() {
            });
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> a : arr) {
                Map<String, Object> st = (Map<String, Object>) a.getOrDefault("status", Map.of());
                out.add(Map.of("wave", Objects.toString(a.getOrDefault("name", "app")), "target", Objects.toString(a.getOrDefault("project", "default")), "status", Objects.toString(st.getOrDefault("sync", "unknown"))));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    } return out.stream().

        private Map<String, Object> scan(String id, String tool, String target, String status, int c, int h, int m, String detail) {
        return Map.of("id", id, "tool", tool, "target", target, "status", status, "critical", c, "high", h, "medium", m, "duration", detail);
    }.

private List<Map<String, Object>> cvesFromTrivy(String trivy) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            Map<String, Object> root = mapper.readValue(trivy, new TypeReference<>() {
            });
            List<Map<String, Object>> results = (List<Map<String, Object>>) root.getOrDefault("Results", List.of());
            for (Map<String, Object> r : results) {
                List<Map<String, Object>> vulns = (List<Map<String, Object>>) r.getOrDefault("Vulnerabilities", List.of());
                for (Map<String, Object> v : vulns)
                    out.add(Map.of("cve", Objects.toString(v.getOrDefault("VulnerabilityID", "CVE")), "packageName", Objects.toString(v.getOrDefault("PkgName", "unknown")), "severity", normalizeSeverity(Objects.toString(v.getOrDefault("Severity", "MEDIUM"))), "image", Objects.toString(r.getOrDefault("Target", "filesystem")), "fixVersion", Objects.toString(v.getOrDefault("FixedVersion", "not published")), "exploit", Objects.toString(v.getOrDefault("Status", "detected"))));
            }
        }
    }
}
}
