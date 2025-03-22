package dev.nebulaops.gateway.client;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class ToolCommandClient {
    public ToolResult run(List<String> command, int timeoutSeconds) {
        long started = System.nanoTime();
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(false);
            Process p = builder.start();
            String stdout;
            String stderr;
            try (BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                 BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                stdout = out.lines().reduce("", (a, b) -> a + b + "\n");
                stderr = err.lines().reduce("", (a, b) -> a + b + "\n");
            }
            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            long ms = (System.nanoTime() - started) / 1_000_000;
            if (!finished) {
                p.destroyForcibly();
                return new ToolResult(false, 124, "tool timeout", "", "timeout after " + timeoutSeconds + "s", ms, Instant.now().toString());
            }
            return new ToolResult(p.exitValue() == 0, p.exitValue(), p.exitValue() == 0 ? "ok" : "tool exited with code " + p.exitValue(), stdout.trim(), stderr.trim(), ms, Instant.now().toString());
        } catch (Exception e) {
            long ms = (System.nanoTime() - started) / 1_000_000;
            return new ToolResult(false, -1, e.getClass().getSimpleName() + ": " + e.getMessage(), "", "", ms, Instant.now().toString());
        }
    }

    public ToolResult shell(String command, int timeoutSeconds) {
        return run(List.of("sh", "-lc", command), timeoutSeconds);
    }

    public Map<String, Object> unavailable(String tool, ToolResult result) {
        return Map.of("live", false, "tool", tool, "items", List.of(), "toolStatus", result.asMap());
    }
}
