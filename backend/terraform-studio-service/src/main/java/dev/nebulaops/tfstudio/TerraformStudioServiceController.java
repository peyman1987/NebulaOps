package dev.nebulaops.tfstudio;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/terraform-studio")
public class TerraformStudioServiceController {
    @GetMapping("/graph")
    public Map<String, Object> graph() {
        return Map.of("nodes", List.of("VPC", "Subnets", "Load Balancer", "K8s Cluster", "MongoDB", "Redis"), "mode", "digital-twin");
    }

    @GetMapping("/plan")
    public Map<String, Object> plan() {
        return Map.of("add", 12, "change", 3, "destroy", 0, "monthlyCost", 374, "currency", "EUR");
    }

    @GetMapping("/modules")
    public List<String> modules() {
        return List.of("vpc", "subnets", "mongodb", "load-balancer", "k8s-cluster", "observability");
    }
}