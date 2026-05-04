package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.client.ToolCommandClient;
import dev.nebulaops.gateway.client.ToolResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NebulaOps v23.3 — compact runtime diagnostics with real tool output only.
 */
@RestController
public class RuntimeDiagnosticsController {
    private final ToolCommandClient tools;

    public RuntimeDiagnosticsController(ToolCommandClient tools) {
        this.tools = tools;
    }

    @GetMapping("/api/runtime/diagnostics")
    public Map<String, Object> diagnostics() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", true);
        out.put("realDataOnly", true);
        out.put("mode", "RUNTIME_DIAGNOSTICS");
        out.put("generatedAt", Instant.now().toString());

        Map<String, Object> checks = new LinkedHashMap<>();
        checks.put("gateway", state(true, "UP", "Gateway diagnostics endpoint is reachable", null));
        checks.put("docker", probe("docker", "command -v docker >/dev/null 2>&1 && docker info --format '{{.ServerVersion}}'", 4));
        checks.put("kubectl", probe("kubectl", "command -v kubectl >/dev/null 2>&1 && kubectl version --client=true --output=json >/dev/null 2>&1", 4));
        checks.put("kubernetesCluster", probe("kubernetesCluster", "kubectl --request-timeout=4s cluster-info >/dev/null", 5));
        checks.put("kubeConfig", probe("kubeConfig", "test -s /kube/config", 2));
        checks.put("extensionRegistry", probe("extensionRegistry", "command -v docker >/dev/null 2>&1 && (docker ps --format '{{.Names}}' | grep -qx nebulaops-v23-3-registry)", 4));
        out.put("checks", checks);

        long unavailable = checks.values().stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .filter(item -> !"UP".equals(String.valueOf(item.get("state"))))
                .count();
        out.put("state", unavailable == 0 ? "UP" : "DEGRADED");
        out.put("unavailableChecks", unavailable);
        return out;
    }

    private Map<String, Object> probe(String tool, String command, int timeoutSeconds) {
        ToolResult result = shell(command, timeoutSeconds);
        if (result.ok()) {
            return state(true, "UP", tool + " is reachable", result);
        }
        String state = result.exitCode() == 124 ? "TIMEOUT" : "UNAVAILABLE";
        return state(false, state, tool + " is not reachable or not configured", result);
    }

    private ToolResult shell(String command, int timeoutSeconds) {
        return tools.shell("export PATH=/opt/nebula-tools:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; export KUBECONFIG=/kube/config; " + command, timeoutSeconds);
    }

    private Map<String, Object> state(boolean live, String state, String message, ToolResult result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", live);
        out.put("state", state);
        out.put("message", message);
        if (result != null) out.put("toolStatus", resultMap(result));
        return out;
    }

    private Map<String, Object> resultMap(ToolResult result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", result.ok());
        out.put("exitCode", result.exitCode());
        out.put("message", result.message());
        out.put("stdout", result.stdout() == null ? "" : result.stdout());
        out.put("stderr", result.stderr() == null ? "" : result.stderr());
        out.put("durationMs", result.durationMs());
        out.put("executedAt", result.executedAt());
        return out;
    }
}
