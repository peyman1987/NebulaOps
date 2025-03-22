package dev.nebulaops.aiops.service;

import dev.nebulaops.aiops.client.ProcessExecutor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiOpsService {
    private final ProcessExecutor exec;

    public AiOpsService(ProcessExecutor exec) {
        this.exec = exec;
    }

    public Map<String, Object> diagnose(String namespace) {
        var events = exec.shell(ns(namespace, "kubectl get events -A --sort-by=.lastTimestamp -o json", "kubectl get events -n " + safe(namespace) + " --sort-by=.lastTimestamp -o json"), 30);
        return Map.of("live", events.ok(), "tool", "kubectl", "data", events.stdout(), "toolStatus", events.status());
    }

    public Map<String, Object> analyze(Map<String, Object> request) {
        String prompt = String.valueOf(request.getOrDefault("prompt", "Analyze current Kubernetes health"));
        String logs = String.valueOf(request.getOrDefault("logs", ""));
        var pods = exec.shell("kubectl get pods -A -o json", 30);
        var events = exec.shell("kubectl get events -A --sort-by=.lastTimestamp -o json", 30);
        boolean live = pods.ok() || events.ok() || !logs.isBlank();
        String rootCause = detectRootCause(logs, pods.stdout(), events.stdout());
        String fix = recommendedFix(rootCause);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("incidentId", "AIOPS-" + Instant.now().toEpochMilli());
        out.put("summary", live ? "Live analysis completed from Kubernetes/events/log input." : "No live Kubernetes/log source was available for analysis.");
        out.put("rootCause", rootCause);
        out.put("confidence", live ? 0.72 : 0.0);
        out.put("blastRadius", extractBlastRadius(logs, pods.stdout()));
        out.put("fix", fix);
        out.put("yaml", "# Suggested action is intentionally not auto-applied. Review before applying.\n# " + fix.replace("\n", " "));
        out.put("events", List.of(event("Live diagnostic", live ? "MEDIUM" : "LOW", live ? "completed" : "no-live-source", rootCause)));
        out.put("nodes", List.of());
        out.put("live", live);
        out.put("toolStatus", Map.of("pods", pods.status(), "events", events.status()));
        out.put("prompt", prompt);
        return out;
    }

    public Map<String, Object> autofix(Map<String, Object> request) {
        String yaml = String.valueOf(request.getOrDefault("yaml", ""));
        if (yaml.isBlank() || yaml.startsWith("#")) {
            return Map.of("live", false, "status", "skipped", "message", "No concrete Kubernetes manifest/action was provided. Nothing applied.");
        }
        try {
            Path tmp = Path.of("/tmp/nebulaops-autofix.yaml");
            Files.writeString(tmp, yaml, StandardCharsets.UTF_8);
            var result = exec.shell("kubectl apply -f " + tmp, 30);
            return Map.of("live", result.ok(), "status", result.ok() ? "applied" : "failed", "tool", "kubectl", "data", result.stdout(), "toolStatus", result.status());
        } catch (Exception e) {
            return Map.of("live", false, "status", "failed", "message", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    public Map<String, Object> logs(String selector, String namespace) {
        String cmd = selector == null || selector.isBlank()
                ? ns(namespace, "kubectl logs -A --tail=200", "kubectl logs -n " + safe(namespace) + " --tail=200")
                : "kubectl logs -n " + safe(namespace) + " -l " + safeSelector(selector) + " --tail=200";
        var r = exec.shell(cmd, 30);
        return Map.of("live", r.ok(), "tool", "kubectl", "data", r.stdout(), "toolStatus", r.status());
    }

    public Map<String, Object> action(String namespace, String deployment, String action) {
        String cmd = switch (action == null ? "" : action) {
            case "restart" -> "kubectl rollout restart deployment/" + safe(deployment) + " -n " + safe(namespace);
            case "scale-zero" -> "kubectl scale deployment/" + safe(deployment) + " --replicas=0 -n " + safe(namespace);
            default -> "echo unsupported action && exit 2";
        };
        var r = exec.shell(cmd, 30);
        return Map.of("live", r.ok(), "tool", "kubectl", "data", r.stdout(), "toolStatus", r.status());
    }

    private String detectRootCause(String logs, String podsJson, String eventsJson) {
        String all = (logs + "\n" + podsJson + "\n" + eventsJson).toLowerCase();
        if (all.contains("crashloopbackoff")) return "One or more pods are in CrashLoopBackOff.";
        if (all.contains("imagepullbackoff") || all.contains("errimagepull"))
            return "A container image cannot be pulled or authenticated.";
        if (all.contains("oomkilled")) return "A container was OOMKilled and needs memory/request tuning.";
        if (all.contains("failedscheduling") || all.contains("insufficient"))
            return "Scheduler cannot place one or more pods because of capacity or constraints.";
        if (all.contains("connection refused") || all.contains("timeout"))
            return "A runtime/service connectivity timeout was detected.";
        return "No obvious high-severity pattern detected in the current live inputs.";
    }

    private String recommendedFix(String rootCause) {
        String r = rootCause.toLowerCase();
        if (r.contains("crashloop"))
            return "Open pod logs, inspect the failing container command/env, then restart rollout after fixing configuration.";
        if (r.contains("image"))
            return "Verify image tag, registry credentials, imagePullSecrets and node registry connectivity.";
        if (r.contains("oom"))
            return "Increase memory limits/requests or reduce application memory usage, then restart the workload.";
        if (r.contains("scheduler")) return "Check node capacity, taints/tolerations, affinity rules and pending PVCs.";
        if (r.contains("timeout")) return "Check service endpoints, DNS, network policies and downstream readiness.";
        return "No automatic fix suggested; continue monitoring live events and logs.";
    }

    private List<String> extractBlastRadius(String logs, String podsJson) {
        List<String> out = new ArrayList<>();
        String all = (logs + "\n" + podsJson).toLowerCase();
        for (String name : List.of("frontend", "gateway", "task", "mongodb", "redis", "rabbitmq", "prometheus", "loki")) {
            if (all.contains(name)) out.add(name);
        }
        return out;
    }

    private Map<String, Object> event(String title, String severity, String status, String recommendation) {
        return Map.of("time", Instant.now().toString(), "service", "cluster", "severity", severity, "title", title, "status", status, "recommendation", recommendation);
    }

    private String ns(String namespace, String all, String one) {
        return namespace == null || namespace.isBlank() || namespace.equals("all") ? all : one;
    }

    private String safe(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9_.:-]", "");
    }

    private String safeSelector(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9_.=:-]", "");
    }
}
