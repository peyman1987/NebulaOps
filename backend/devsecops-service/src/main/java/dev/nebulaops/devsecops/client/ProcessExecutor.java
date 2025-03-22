package dev.nebulaops.devsecops.client;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class ProcessExecutor {
    public Result run(List<String> command, int timeoutSeconds) {
        long started = System.nanoTime();
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(false).start();
            String stdout;
            String stderr;
            try (BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8)); BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                stdout = out.lines().reduce("", (a, b) -> a + b + "\n");
                stderr = err.lines().reduce("", (a, b) -> a + b + "\n");
            }
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Result(false, 124, "timeout", "", "timeout after " + timeoutSeconds + "s", (System.nanoTime() - started) / 1_000_000, Instant.now().toString());
            }
            return new Result(p.exitValue() == 0, p.exitValue(), p.exitValue() == 0 ? "ok" : "exit code " + p.exitValue(), stdout.trim(), stderr.trim(), (System.nanoTime() - started) / 1_000_000, Instant.now().toString());
        } catch (Exception e) {
            return new Result(false, -1, e.getClass().getSimpleName() + ": " + e.getMessage(), "", "", (System.nanoTime() - started) / 1_000_000, Instant.now().toString());
        }
    }

    public Result shell(String command, int timeoutSeconds) {
        return run(List.of("sh", "-lc", command), timeoutSeconds);
    }

    public record Result(boolean ok, int exitCode, String message, String stdout, String stderr, long durationMs,
                         String executedAt) {
        public Map<String, Object> status() {
            return Map.of("ok", ok, "exitCode", exitCode, "message", message, "stderr", stderr == null ? "" : stderr, "durationMs", durationMs, "executedAt", executedAt);
        }
    }
}
