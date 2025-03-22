package dev.nebulaops.gateway.service;

import dev.nebulaops.gateway.client.JsonToolAdapter;
import dev.nebulaops.gateway.client.ToolCommandClient;
import dev.nebulaops.gateway.client.ToolResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TerraformPlatformService {
    private final ToolCommandClient tools;
    private final JsonToolAdapter json;

    public TerraformPlatformService(ToolCommandClient tools, JsonToolAdapter json) {
        this.tools = tools;
        this.json = json;
    }

    public Map<String, Object> init(String workspace) {
        return shell("terraform", "cd " + tfDir(workspace) + " && terraform init -input=false -no-color");
    }

    public Map<String, Object> plan(String workspace) {
        return shell("terraform", "cd " + tfDir(workspace) + " && terraform plan -input=false -no-color -out=tfplan && terraform show -json tfplan");
    }

    public Map<String, Object> graph(String workspace) {
        return shell("terraform", "cd " + tfDir(workspace) + " && terraform graph");
    }

    public Map<String, Object> modules(String workspace) {
        return shell("filesystem", "find " + tfDir(workspace) + " -type f -name '*.tf' -maxdepth 5 -print");
    }

    private Map<String, Object> shell(String tool, String command) {
        ToolResult r = tools.shell(command, 120);
        Object data = r.stdout();
        if (r.ok() && !r.stdout().isBlank() && r.stdout().trim().startsWith("{")) {
            try {
                data = json.parseJson(r.stdout());
            } catch (Exception ignored) {
            }
        }
        return Map.of("live", r.ok(), "tool", tool, "data", data, "toolStatus", r.asMap());
    }

    private String tfDir(String workspace) {
        String base = System.getenv().getOrDefault("TERRAFORM_WORKDIR", "/workspace/terraform");
        String ws = workspace == null || workspace.isBlank() ? "" : "/" + workspace.replaceAll("[^A-Za-z0-9_.:-]", "");
        return base + ws;
    }
}
