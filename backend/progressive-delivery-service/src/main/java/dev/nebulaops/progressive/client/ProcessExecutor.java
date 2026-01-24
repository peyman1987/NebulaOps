package dev.nebulaops.progressive.client;

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
public class ProcessExecutor {
    public Result run(List<String> command, int timeoutSeconds) {
        long started = System.nanoTime();
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(false).start();
            Process runningProcess = process;
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readAll(runningProcess.getInputStream()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readAll(runningProcess.getErrorStream()));

            if (!runningProcess.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                runningProcess.destroyForcibly();
                stdoutFuture.cancel(true);
                stderrFuture.cancel(true);
                return new Result(false, 124, "timeout", "", "timeout after " + timeoutSeconds + "s", (System.nanoTime() - started) / 1_000_000, Instant.now().toString());
            }

            String stdout = stdoutFuture.get(3, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(3, TimeUnit.SECONDS);
            int exitCode = runningProcess.exitValue();
            return new Result(exitCode == 0, exitCode, exitCode == 0 ? "ok" : "exit code " + exitCode, stdout.trim(), stderr.trim(), (System.nanoTime() - started) / 1_000_000, Instant.now().toString());
        } catch (Exception e) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            return new Result(false, -1, e.getClass().getSimpleName() + ": " + e.getMessage(), "", "", (System.nanoTime() - started) / 1_000_000, Instant.now().toString());
        }
    }

    public Result shell(String command, int timeoutSeconds) {
        return run(List.of("sh", "-lc", command), timeoutSeconds);
    }

    private String readAll(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "";
        }
    }

    public record Result(boolean ok, int exitCode, String message, String stdout, String stderr, long durationMs,
                         String executedAt) {
        public Map<String, Object> status() {
            return Map.of("ok", ok, "exitCode", exitCode, "message", message, "stderr", stderr == null ? "" : stderr, "durationMs", durationMs, "executedAt", executedAt);
        }
    }
}
