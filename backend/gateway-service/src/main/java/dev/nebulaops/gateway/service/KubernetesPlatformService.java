package dev.nebulaops.gateway.service;

import dev.nebulaops.gateway.client.JsonToolAdapter;
import dev.nebulaops.gateway.client.ToolCommandClient;
import dev.nebulaops.gateway.client.ToolResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * v21.2 — Kubernetes platform service.
 * Timeout reduced: snapshot (nodes) uses 8s, events 10s, others 12s.
 * Fast fail ensures /api/kubernetes/snapshot returns promptly even when
 * kubectl is unavailable — never causes a 502 cascade.
 */
@Service
public class KubernetesPlatformService {
    private final ToolCommandClient tools;
    private final JsonToolAdapter json;

    public KubernetesPlatformService(ToolCommandClient tools, JsonToolAdapter json) {
        this.tools = tools;
        this.json = json;
    }

    public Map<String, Object> cluster() {
        return kubectl("cluster", "kubectl config view --minify -o json", 8);
    }

    public Map<String, Object> nodes() {
        // 8s timeout — used by /api/kubernetes/snapshot, must respond fast
        return kubectl("nodes", "kubectl get nodes -o json", 8);
    }

    public Map<String, Object> namespaces() {
        return kubectl("namespaces", "kubectl get namespaces -o json", 10);
    }

    public Map<String, Object> events(String namespace) {
        return kubectl("events",
            ns(namespace,
               "kubectl get events -o json",
               "kubectl get events -n " + safe(namespace) + " -o json"), 10);
    }

    public Map<String, Object> resource(String kind, String namespace) {
        return kubectl(kind,
            ns(namespace,
               "kubectl get " + safeKind(kind) + " -A -o json",
               "kubectl get " + safeKind(kind) + " -n " + safe(namespace) + " -o json"), 12);
    }

    public Map<String, Object> helmReleases(String namespace) {
        return helm(ns(namespace, "helm list -A -o json", "helm list -n " + safe(namespace) + " -o json"));
    }

    private Map<String, Object> kubectl(String resource, String command, int timeoutSeconds) {
        return jsonCommand("kubectl", resource, command, timeoutSeconds);
    }

    private Map<String, Object> helm(String command) {
        return jsonCommand("helm", "releases", command, 12);
    }

    private Map<String, Object> jsonCommand(String tool, String resource, String command, int timeoutSeconds) {
        ToolResult r = tools.shell(command, timeoutSeconds);
        Object body = List.of();
        if (r.ok() && !r.stdout().isBlank()) {
            try {
                body = json.parseJson(r.stdout());
            } catch (Exception e) {
                body = Map.of("parseError", e.getMessage(), "raw", r.stdout());
            }
        }
        return Map.of("live", r.ok(), "tool", tool, "resource", resource,
                      "data", body, "toolStatus", r.asMap());
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
