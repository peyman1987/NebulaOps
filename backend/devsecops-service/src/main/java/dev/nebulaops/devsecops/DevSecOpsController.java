package dev.nebulaops.devsecops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/devsecops")
public class DevSecOpsController {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path repo = Paths.get(System.getenv().getOrDefault("REPO_PATH", "/workspace"));

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        ToolResult trivy = run(repo, 120, "trivy", "fs", "--format", "json", "--quiet", repo.toString());
        List<Map<String, Object>> cves = trivy.ok ? parseTrivy(trivy.stdout) : List.of();
        ToolResult secrets = run(repo, 30, "sh", "-lc", "grep -RInE '(AWS_SECRET_ACCESS_KEY|PRIVATE KEY|password=|token=|api[_-]?key)' --exclude-dir=.git --exclude-dir=target --exclude-dir=node_modules . | head -50");
        List<String> secretFindings = secrets.stdout.isBlank() ? List.of() : Arrays.stream(secrets.stdout.split("\\R")).toList();
        int critical = count(cves, "CRITICAL"), high = count(cves, "HIGH"), medium = count(cves, "MEDIUM");
        int risk = Math.min(100, critical * 20 + high * 10 + medium * 3 + secretFindings.size() * 5);
        return Map.of(
                "version", "20.5-real-backend",
                "riskScore", risk,
                "updatedAt", Instant.now().toString(),
                "live", trivy.ok || secrets.ok,
                "scans", List.of(
                        scan("TRIVY-FS", "Trivy", repo.toString(), trivy.ok ? "COMPLETED" : "UNAVAILABLE", critical, high, medium, trivy.message),
                        scan("SECRETS-GREP", "grep", repo.toString(), secrets.ok ? "COMPLETED" : "UNAVAILABLE", 0, secretFindings.size(), 0, secrets.message)
                ),
                "cves", cves.stream().limit(50).toList(),
                "secrets", secretFindings,
                "toolStatus", Map.of("trivy", trivy.message, "secrets", secrets.message)
        );
    }

    @PostMapping("/scan")
    public Map<String, Object> scanNow(@RequestBody(required = false) Map<String, Object> body) {
        String target = Objects.toString(body == null ? repo : body.getOrDefault("target", repo.toString()));
        ToolResult result = run(repo, 180, "trivy", "fs", "--format", "json", "--quiet", target);
        return Map.of("status", result.ok ? "completed" : "failed", "target", target, "scanId", "LIVE-" + System.currentTimeMillis(), "startedAt", Instant.now().toString(), "toolStatus", result.message, "cves", result.ok ? parseTrivy(result.stdout).stream().limit(50).toList() : List.of());
    }

    private List<Map<String, Object>> parseTrivy(String json) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            Map<String, Object> root = mapper.readValue(json, new TypeReference<>() {
            });
            List<Map<String, Object>> results = (List<Map<String, Object>>) root.getOrDefault("Results", List.of());
            for (Map<String, Object> r : results) {
                List<Map<String, Object>> vulns = (List<Map<String, Object>>) r.getOrDefault("Vulnerabilities", List.of());
                for (Map<String, Object> v : vulns)
                    out.add(Map.of(
                            "cve", Objects.toString(v.getOrDefault("VulnerabilityID", "")),
                            "packageName", Objects.toString(v.getOrDefault("PkgName", "")),
                            "severity", Objects.toString(v.getOrDefault("Severity", "UNKNOWN")),
                            "fixVersion", Objects.toString(v.getOrDefault("FixedVersion", "")),
                            "target", Objects.toString(r.getOrDefault("Target", ""))
                    ));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private int count(List<Map<String, Object>> rows, String severity) {
        return (int) rows.stream().filter(r -> severity.equalsIgnoreCase(Objects.toString(r.get("severity")))).count();
    }

    private Map<String, Object> scan(String id, String tool, String target, String status, int critical, int high, int medium, String detail) {
        return Map.of("id", id, "tool", tool, "target", target, "status", status, "critical", critical, "high", high, "medium", medium, "detail", detail);
    }

    private ToolResult run(Path cwd, int timeoutSeconds, String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(cwd.toFile());
            Process p = pb.start();
            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                p.destroyForcibly();
                return new ToolResult(false, out, err, "timeout: " + String.join(" ", cmd));
            }
            return new ToolResult(p.exitValue() == 0, out, err, String.join(" ", cmd) + " exit=" + p.exitValue() + (err.isBlank() ? "" : " stderr=" + err));
        } catch (Exception e) {
            return new ToolResult(false, "", e.getMessage(), "tool unavailable: " + cmd[0] + " - " + e.getMessage());
        }
    }

    record ToolResult(boolean ok, String stdout, String stderr, String message) {
    }
}
