package dev.nebulaops.tfstudio;

import dev.nebulaops.tfstudio.service.TerraformService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/terraform-studio")
public class TerraformStudioServiceController {
    private final TerraformService service;

    public TerraformStudioServiceController(TerraformService service) {
        this.service = service;
    }

    @GetMapping("/graph")
    public Map<String, Object> graph(@RequestParam(required = false) String workspace) {
        return service.graph(workspace);
    }

    @GetMapping("/plan")
    public Map<String, Object> plan(@RequestParam(required = false) String workspace) {
        return service.plan(workspace);
    }

    @GetMapping("/modules")
    public Map<String, Object> modules(@RequestParam(required = false) String workspace) {
        return service.modules(workspace);
    }
}
