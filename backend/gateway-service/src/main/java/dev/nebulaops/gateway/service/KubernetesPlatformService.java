package dev.nebulaops.gateway.service;

import dev.nebulaops.gateway.client.JsonToolAdapter;
import dev.nebulaops.gateway.client.ToolCommandClient;
import dev.nebulaops.gateway.client.ToolResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class KubernetesPlatformService {
    private final ToolCommandClient tools;
    private final JsonToolAdapter json;

    public KubernetesPlatformService(ToolCommandClient tools, JsonToolAdapter json) {
        this.tools = tools;
        this.json = json;
    }

    public Map<String, Object> cluster() {
        return kubectl("cluster", "kubectl config view --minify -o json");
    }

    public Map<String, Object> nodes() {
        return kubectl("nodes", "kubectl get nodes -o json");
    }

    public Map<String, Object> namespaces() {
        return kubectl("namespaces", "kubectl get namespaces -o json");
    }

    public Map<String, Object> events(String namespace) {
        return kubectl("events", ns(namespace, "kubectl get events -o json", "kubectl get events -n " + safe(namespace) + " -o json"));
    }

    public Map<String, Object> resource(String kind, String namespace) {
        return kubectl(kind, ns(namespace, "kubectl get " + safeKind(kind) + " -A -o json", "kubectl get " + safeKind(kind) + " -n " + safe(namespace) + " -o json"));
    }

    public Map<String, Object> helmReleases(String namespace) {
        return helm(ns(namespace, "helm list -A -o json", "helm list -n " + safe(namespace) + " -o json"));
    }

    private Map<String, Object> kubectl(String resource, String command) {
        return jsonCommand("kubectl", resource, command);
    }

    private Map<String, Object> helm(String command) {
        return jsonCommand("helm", "releases", command);
    }

    private Map<String, Object> jsonCommand(String tool, String resource, String command) {
        ToolResult r = tools.shell(command, 15);
        Object body = List.of();
        if (r.ok() && !r.stdout().isBlank()) {
            try {
                body = json.parseJson(r.stdout());
            } catch (Exception e) {
                body = Map.of("parseError", e.getMessage(), "raw", r.stdout());
            }
        }
        return Map.of("live", r.ok(), "tool", tool, "resource", resource, "data", body, "toolStatus", r.asMap());
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
}
