package dev.nebulaops.gateway.client;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class ToolCommandClient {
    public ToolResult run(List<String> command, int timeoutSeconds) {
        long started = System.nanoTime();
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(false);
            process = builder.start();
            Process runningProcess = process;
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readAll(runningProcess.getInputStream()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readAll(runningProcess.getErrorStream()));

            boolean finished = process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
            long ms = (System.nanoTime() - started) / 1_000_000;
            if (!finished) {
                process.destroyForcibly();
                stdoutFuture.cancel(true);
                stderrFuture.cancel(true);
                process.waitFor(2, TimeUnit.SECONDS);
                return new ToolResult(false, 124, "tool timeout", "", "timeout after " + timeoutSeconds + "s", ms, Instant.now().toString());
            }

            String stdout = stdoutFuture.get(3, TimeUnit.SECONDS).trim();
            String stderr = stderrFuture.get(3, TimeUnit.SECONDS).trim();
            int exitCode = process.exitValue();
            return new ToolResult(exitCode == 0, exitCode, exitCode == 0 ? "ok" : "tool exited with code " + exitCode, stdout, stderr, ms, Instant.now().toString());
        } catch (Exception e) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
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

    private String readAll(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "";
        }
    }
}
