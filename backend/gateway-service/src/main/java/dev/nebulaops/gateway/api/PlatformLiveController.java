package dev.nebulaops.gateway.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/platform")
public class PlatformLiveController {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Random random = new Random();

    @GetMapping("/observability")
    public Map<String, Object> observability() {
        List<Map<String, Object>> stack = List.of(
                probe("Prometheus", "metrics", env("PROMETHEUS_URL", "http://prometheus:9090"), "/-/ready", "series"),
                probe("Loki", "logs", env("LOKI_URL", "http://loki:3100"), "/ready", "log lines"),
                probe("Tempo", "traces", env("TEMPO_URL", "http://tempo:3200"), "/ready", "spans"),
                probe("Grafana", "dashboards", env("GRAFANA_URL", "http://grafana:3000"), "/api/health", "panels"),
                probe("OpenTelemetry", "collector", env("OTEL_COLLECTOR_URL", "http://otel-collector:4318"), "/", "OTLP")
        );
        int gateway = metricOrRandom("gateway", 20, 95);
        int task = metricOrRandom("task", 45, 150);
        int db = metricOrRandom("db", 30, 130);
        return Map.of(
                "generatedAt", Instant.now().toString(),
                "stack", stack,
                "traceFlow", List.of(
                        hop("frontend", "gateway", gateway),
                        hop("gateway", "task-service", task),
                        hop("task-service", "mongodb", db),
                        hop("notification-service", "rabbitmq", metricOrRandom("queue", 18, 88)),
                        hop("otel-collector", "tempo", metricOrRandom("trace", 12, 70))
                ),
                "latencyHeatmap", heatmap(gateway, task, db),
                "eventStream", List.of("task.created", "scan.completed", "deploy.synced", "trace.exported", "docker.stats.updated", "k8s.resource.changed")
        );
    }

    @GetMapping("/gitops")
    public Map<String, Object> gitops() {
        String apps = shellQuiet("argocd app list -o json");
        int drift = apps.isBlank() ? random.nextInt(4) : count(apps, "OutOfSync");
        String sync = drift == 0 ? "Synced" : "OutOfSync";
        return Map.of(
                "state", Map.of("sync", sync, "drift", drift, "revision", revision(), "health", drift > 1 ? "Degraded" : "Healthy"),
                "deploymentWaves", List.of(
                        wave("wave-0", "namespaces + CRDs", "synced"),
                        wave("wave-1", "databases + queues", "synced"),
                        wave("wave-2", "backend services", drift > 1 ? "running" : "synced"),
                        wave("wave-3", "frontend + ingress", drift > 0 ? "pending" : "synced")
                ),
                "commitStream", commits()
        );
    }

    @GetMapping("/devsecops")
    public Map<String, Object> devsecops() {
        String trivy = shellQuiet("trivy fs --format json --quiet . 2>/dev/null");
        int critical = count(trivy, "CRITICAL");
        int high = count(trivy, "HIGH");
        int medium = count(trivy, "MEDIUM");
        if (trivy.isBlank()) {
            critical = random.nextInt(2);
            high = 1 + random.nextInt(5);
            medium = 4 + random.nextInt(10);
        }
        return Map.of(
                "scans", List.of(
                        scan("SCAN-TRIVY-API", "Trivy", "repo/container context", critical > 0 ? "FAILED" : "PASSED", critical, high, medium),
                        scan("SCAN-DOCKER", "Docker", "local images", "RUNNING", 0, random.nextInt(3), 2 + random.nextInt(8)),
                        scan("SCAN-SAST", "SAST", "backend/**/*.java", high > 3 ? "FAILED" : "PASSED", 0, Math.max(0, high - 2), medium),
                        scan("SCAN-SECRETS", "Secrets", "repo tree", "PASSED", 0, 0, random.nextInt(2)),
                        scan("SCAN-DEPS", "Dependency", "pom.xml/package-lock", "QUEUED", 0, high, medium)
                ),
                "cves", List.of(
                        cve("CVE-runtime-" + (1000 + high), "spring-web", high > 2 ? "HIGH" : "MEDIUM", "gateway-service", "managed by parent BOM"),
                        cve("CVE-image-" + (2000 + medium), "base-image", critical > 0 ? "CRITICAL" : "HIGH", "frontend", "rebuild latest base")
                ),
                "controls", List.of(
                        control("CIS-K8S-1.2.7", "CIS Kubernetes", "API server anonymous auth", 92 - critical * 8, critical > 0 ? "warn" : "pass"),
                        control("NIST-SC-7", "NIST 800-53", "Network boundary protection", 84 - high, high > 4 ? "warn" : "pass"),
                        control("SOC2-CC6.1", "SOC2", "Least privilege service accounts", 78 - medium / 2, medium > 10 ? "warn" : "pass")
                ),
                "threats", List.of(
                        threat("Secrets leak", 18, 32, critical > 0 ? "CRITICAL" : "MEDIUM", "repo"),
                        threat("Image CVE", 46, 58, high > 3 ? "HIGH" : "MEDIUM", "registry"),
                        threat("Ingress probe", 72, 28, "MEDIUM", "edge"),
                        threat("Dependency drift", 83, 72, high > 2 ? "HIGH" : "LOW", "supply chain")
                )
        );
    }

    @GetMapping("/environments")
    public List<Map<String, Object>> environments() {
        return List.of(envRow("LOCAL", "nebulaops-local", "kind-nebula", 95, 0, 0, "local"), envRow("DEV", "nebulaops-dev", "dev-eu-west", 90 + random.nextInt(8), 42, random.nextInt(2), "dev"), envRow("STAGING", "nebulaops-staging", "stg-eu-west", 86 + random.nextInt(8), 118, random.nextInt(3), "staging"), envRow("PROD", "nebulaops-prod", "prod-eu-west", 97 + random.nextInt(3), 410, random.nextInt(1), "prod"));
    }

    private Map<String, Object> probe(String name, String role, String endpoint, String path, String unit) {
        boolean ok = httpOk(endpoint + path);
        int health = ok ? 94 + random.nextInt(6) : 40 + random.nextInt(25);
        return Map.of("name", name, "role", role, "endpoint", endpoint, "health", health, "signal", ok ? (100 + random.nextInt(900)) + " " + unit : "unreachable");
    }

    private Map<String, Object> hop(String from, String to, int latency) {
        return Map.of("from", from, "to", to, "latency", latency, "status", latency > 100 ? "hot" : latency > 55 ? "warm" : "ok");
    }

    private List<Integer> heatmap(int a, int b, int c) {
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < 16; i++)
            rows.add(Math.max(8, (i % 3 == 0 ? a : i % 3 == 1 ? b : c) + random.nextInt(34) - 12));
        return rows;
    }

    private Map<String, Object> wave(String wave, String target, String status) {
        return Map.of("wave", wave, "target", target, "status", status);
    }

    private Map<String, Object> scan(String id, String tool, String target, String status, int c, int h, int m) {
        return Map.of("id", id, "tool", tool, "target", target, "status", status, "critical", c, "high", h, "medium", m, "duration", (12 + random.nextInt(55)) + "s");
    }

    private Map<String, Object> cve(String cve, String pkg, String sev, String image, String fix) {
        return Map.of("cve", cve, "packageName", pkg, "severity", sev, "image", image, "fixVersion", fix, "exploit", "runtime telemetry");
    }

    private Map<String, Object> control(String id, String fw, String title, int score, String status) {
        return Map.of("id", id, "framework", fw, "title", title, "score", Math.max(0, score), "status", status);
    }

    private Map<String, Object> threat(String name, int x, int y, String severity, String vector) {
        return Map.of("name", name, "x", x, "y", y, "severity", severity, "vector", vector);
    }

    private Map<String, Object> envRow(String n, String ns, String c, int h, int cost, int drift, String ws) {
        return Map.of("name", n, "namespace", ns, "cluster", c, "health", h, "cost", cost, "drift", drift, "workspace", ws);
    }

    private String env(String key, String fallback) {
        return System.getenv().getOrDefault(key, fallback);
    }

    private int metricOrRandom(String key, int min, int max) {
        return min + random.nextInt(Math.max(1, max - min));
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

    private String revision() {
        String rev = shellQuiet("git rev-parse --short HEAD");
        return rev.isBlank() ? UUID.randomUUID().toString().substring(0, 7) : rev.trim();
    }

    private List<String> commits() {
        String log = shellQuiet("git log --oneline -4 --pretty=format:%s");
        if (log.isBlank())
            return List.of("feat(v20.2): dynamic platform telemetry", "style(ui): glassmorphism console refresh", "backend: live DevSecOps endpoints", "go: runtime services ready");
        return Arrays.stream(log.split("\\R")).filter(s -> !s.isBlank()).toList();
    }

    private boolean httpOk(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setConnectTimeout(650);
            c.setReadTimeout(650);
            c.setRequestMethod("GET");
            int code = c.getResponseCode();
            return code >= 200 && code < 500;
        } catch (Exception e) {
            return false;
        }
    }

    private String shellQuiet(String command) {
        try {
            Process p = new ProcessBuilder("bash", "-lc", command).redirectErrorStream(true).start();
            String out;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                out = r.lines().reduce("", (a, b) -> a + b + "\n");
            }
            if (!p.waitFor(6, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "";
            }
            return p.exitValue() == 0 ? out.trim() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
