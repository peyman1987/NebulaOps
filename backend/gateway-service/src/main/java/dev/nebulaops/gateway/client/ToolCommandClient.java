package dev.nebulaops.gateway.client;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * v21.2-fixed — Executes host CLI tools (kubectl, docker, helm, trivy).
 *
 * Tools are installed natively in the Docker image (see Dockerfile multi-stage).
 * ProcessBuilder inherits the JVM environment which already has the correct PATH.
 * KUBECONFIG is forwarded from the JVM env so kubectl can reach the cluster.
 */
@Component
public class ToolCommandClient {

    public ToolResult run(List<String> command, int timeoutSeconds) {
        long started = System.nanoTime();
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(false);

            // Forward KUBECONFIG explicitly so kubectl finds the kube config
            Map<String, String> env = builder.environment();
            String kubeconfig = System.getenv("KUBECONFIG");
            if (kubeconfig != null && !kubeconfig.isBlank()) {
                env.put("KUBECONFIG", kubeconfig);
            }
            // Ensure /usr/local/bin is on PATH (where we install kubectl/helm/docker)
            String existingPath = env.getOrDefault("PATH", "");
            if (!existingPath.contains("/usr/local/bin")) {
                env.put("PATH", "/usr/local/bin:" + existingPath);
            }

            Process p = builder.start();
            String stdout;
            String stderr;
            try (BufferedReader out = new BufferedReader(
                     new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                 BufferedReader err = new BufferedReader(
                     new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                stdout = out.lines().reduce("", (a, b) -> a + b + "\n");
                stderr = err.lines().reduce("", (a, b) -> a + b + "\n");
            }
            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            long ms = (System.nanoTime() - started) / 1_000_000;
            if (!finished) {
                p.destroyForcibly();
                return new ToolResult(false, 124, "tool timeout", "",
                    "timeout after " + timeoutSeconds + "s", ms, Instant.now().toString());
            }
            int exit = p.exitValue();
            return new ToolResult(exit == 0, exit,
                exit == 0 ? "ok" : "tool exited with code " + exit,
                stdout.trim(), stderr.trim(), ms, Instant.now().toString());
        } catch (Exception e) {
            long ms = (System.nanoTime() - started) / 1_000_000;
            return new ToolResult(false, -1,
                e.getClass().getSimpleName() + ": " + e.getMessage(),
                "", "", ms, Instant.now().toString());
        }
    }

    public ToolResult shell(String command, int timeoutSeconds) {
        return run(List.of("sh", "-c", command), timeoutSeconds);
    }

    public Map<String, Object> unavailable(String tool, ToolResult result) {
        return Map.of("live", false, "tool", tool, "items", List.of(),
                      "toolStatus", result.asMap());
    }
}
