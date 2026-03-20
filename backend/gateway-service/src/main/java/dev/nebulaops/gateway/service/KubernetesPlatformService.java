package dev.nebulaops.gateway.service;

import dev.nebulaops.gateway.client.JsonToolAdapter;
import dev.nebulaops.gateway.client.ToolCommandClient;
import dev.nebulaops.gateway.client.ToolResult;
import dev.nebulaops.gateway.kubernetes.KubeConfigRegistryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class KubernetesPlatformService {
    private final ToolCommandClient tools;
    private final JsonToolAdapter json;
    private final ObjectProvider<KubeConfigRegistryService> kubeConfigRegistry;

    public KubernetesPlatformService(ToolCommandClient tools, JsonToolAdapter json,
                                     ObjectProvider<KubeConfigRegistryService> kubeConfigRegistry) {
        this.tools = tools;
        this.json = json;
        this.kubeConfigRegistry = kubeConfigRegistry;
    }

    public Map<String, Object> cluster() { return cluster(null); }
    public Map<String, Object> nodes() { return nodes(null); }
    public Map<String, Object> namespaces() { return namespaces(null); }
    public Map<String, Object> events(String namespace) { return events(namespace, null); }
    public Map<String, Object> resource(String kind, String namespace) { return resource(kind, namespace, null); }
    public Map<String, Object> helmReleases(String namespace) { return helmReleases(namespace, null); }

    public Map<String, Object> cluster(String clusterId) {
        return kubectl("cluster", "kubectl config view --minify -o json", clusterId);
    }

    public Map<String, Object> nodes(String clusterId) {
        return kubectl("nodes", "kubectl get nodes -o json", clusterId);
    }

    public Map<String, Object> namespaces(String clusterId) {
        return kubectl("namespaces", "kubectl get namespaces -o json", clusterId);
    }

    public Map<String, Object> events(String namespace, String clusterId) {
        return kubectl("events", ns(namespace, "kubectl get events -o json", "kubectl get events -n " + safe(namespace) + " -o json"), clusterId);
    }

    public Map<String, Object> resource(String kind, String namespace, String clusterId) {
        return kubectl(kind, ns(namespace, "kubectl get " + safeKind(kind) + " -A -o json", "kubectl get " + safeKind(kind) + " -n " + safe(namespace) + " -o json"), clusterId);
    }

    public Map<String, Object> helmReleases(String namespace, String clusterId) {
        return helm(ns(namespace, "helm list -A -o json", "helm list -n " + safe(namespace) + " -o json"), clusterId);
    }

    public ToolResult runKubectl(String command, String clusterId, int timeoutSeconds) {
        return shellWithKubeconfig(command, clusterId, timeoutSeconds);
    }

    public Map<String, Object> currentContextSummary() {
        ToolResult context = tools.shell("kubectl config current-context", 8);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", "current-context");
        out.put("name", context.ok() ? context.stdout().trim() : "kubectl current context");
        out.put("source", "local-kubectl");
        out.put("live", context.ok());
        out.put("status", context.ok() ? "CONNECTED" : "UNAVAILABLE");
        out.put("toolStatus", context.asMap());
        out.put("generatedAt", Instant.now().toString());
        return out;
    }

    private Map<String, Object> kubectl(String resource, String command, String clusterId) {
        return jsonCommand("kubectl", resource, command, clusterId);
    }

    private Map<String, Object> helm(String command, String clusterId) {
        return jsonCommand("helm", "releases", command, clusterId);
    }

    private Map<String, Object> jsonCommand(String tool, String resource, String command, String clusterId) {
        ToolResult r = shellWithKubeconfig(command, clusterId, 15);
        Object body = List.of();
        if (r.ok() && !r.stdout().isBlank()) {
            try {
                body = json.parseJson(r.stdout());
            } catch (Exception e) {
                body = Map.of("parseError", e.getMessage(), "raw", r.stdout());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", r.ok());
        out.put("tool", tool);
        out.put("resource", resource);
        out.put("clusterId", blank(clusterId) ? "current-context" : clusterId);
        out.put("data", body);
        out.put("toolStatus", r.asMap());
        if (!r.ok()) out.put("error", explicitKubernetesError(r));
        return out;
    }

    private ToolResult shellWithKubeconfig(String command, String clusterId, int timeoutSeconds) {
        if (blank(clusterId) || "current-context".equals(clusterId) || "current".equals(clusterId)) {
            return tools.shell(command, timeoutSeconds);
        }
        KubeConfigRegistryService registry = kubeConfigRegistry.getIfAvailable();
        if (registry == null) {
            return new ToolResult(false, -1, "KUBECONFIG_REGISTRY_UNAVAILABLE", "", "MongoDB-backed kubeconfig registry is not configured", 0, Instant.now().toString());
        }
        Path tmp = null;
        try {
            tmp = registry.writeTempKubeconfig(clusterId);
            String prepared = applyKubeconfig(command, tmp);
            return tools.shell(prepared, timeoutSeconds);
        } catch (Exception e) {
            return new ToolResult(false, -1, "KUBECONFIG_UNAVAILABLE", "", e.getMessage(), 0, Instant.now().toString());
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (Exception ignored) { }
            }
        }
    }

    private String applyKubeconfig(String command, Path kubeconfig) {
        String q = quote(kubeconfig.toAbsolutePath().toString());
        if (command.matches("^\\s*kubectl\\b.*")) return command.replaceFirst("^\\s*kubectl\\b", "kubectl --kubeconfig " + q);
        if (command.matches("^\\s*helm\\b.*")) return command.replaceFirst("^\\s*helm\\b", "helm --kubeconfig " + q);
        return command;
    }

    private String explicitKubernetesError(ToolResult r) {
        String msg = (r.stderr() == null || r.stderr().isBlank()) ? r.message() : r.stderr();
        String text = msg == null ? "" : msg.toLowerCase();
        if (text.contains("connection refused") || text.contains("no route to host")) return "KUBERNETES_API_UNREACHABLE: " + msg;
        if (text.contains("forbidden") || text.contains("unauthorized")) return "KUBERNETES_PERMISSION_DENIED: " + msg;
        if (text.contains("no context") || text.contains("current-context")) return "KUBECONFIG_CONTEXT_MISSING: " + msg;
        if (text.contains("certificate") || text.contains("x509")) return "KUBECONFIG_CERTIFICATE_ERROR: " + msg;
        return msg;
    }

    private String ns(String namespace, String all, String namespaced) {
        return namespace == null || namespace.isBlank() || namespace.equals("all") ? all : namespaced;
    }

    private String safe(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9_.:-]", "");
    }

    private String safeKind(String s) {
        return safe(s).toLowerCase();
    }

    private boolean blank(String s) { return s == null || s.isBlank(); }

    private String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
