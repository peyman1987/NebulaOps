package dev.nebulaops.tfstudio.service;

import dev.nebulaops.tfstudio.client.JsonAdapter;
import dev.nebulaops.tfstudio.client.ProcessExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class TerraformService {
    private final ProcessExecutor exec;
    private final JsonAdapter json;

    public TerraformService(ProcessExecutor exec, JsonAdapter json) {
        this.exec = exec;
        this.json = json;
    }

    public Map<String, Object> graph(String workspace) {
        return run("terraform", "cd " + dir(workspace) + " && terraform graph", 60, false);
    }

    public Map<String, Object> plan(String workspace) {
        return run("terraform", "cd " + dir(workspace) + " && terraform init -input=false -no-color >/tmp/tfinit.log && terraform plan -input=false -no-color -out=tfplan && terraform show -json tfplan", 180, true);
    }

    public Map<String, Object> modules(String workspace) {
        return run("filesystem", "find " + dir(workspace) + " -type f -name '*.tf' -maxdepth 6 -print", 30, false);
    }

    private Map<String, Object> run(String tool, String cmd, int t, boolean parseJson) {
        var r = exec.shell(cmd, t);
        Object data = r.stdout();
        if (parseJson && r.ok() && !r.stdout().isBlank()) {
            try {
                data = json.parse(r.stdout());
            } catch (Exception e) {
                data = Map.of("raw", r.stdout(), "parseError", e.getMessage());
            }
        }
        return Map.of("live", r.ok(), "tool", tool, "data", data, "toolStatus", r.status());
    }

    private String dir(String workspace) {
        String base = System.getenv().getOrDefault("TERRAFORM_WORKDIR", "/workspace/terraform");
        return base + (workspace == null || workspace.isBlank() ? "" : "/" + workspace.replaceAll("[^A-Za-z0-9_.:-]", ""));
    }
}
