package dev.nebulaops.devsecops;

import dev.nebulaops.devsecops.service.DevSecOpsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/devsecops")
public class DevSecOpsController {
    private final DevSecOpsService service;

    public DevSecOpsController(DevSecOpsService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> scan(@RequestParam(defaultValue = ".") String path) {
        return service.repositoryScan(path);
    }

    @GetMapping("/secrets")
    public Map<String, Object> secrets(@RequestParam(defaultValue = ".") String path) {
        return service.secretScan(path);
    }

    @GetMapping("/image")
    public Map<String, Object> image(@RequestParam String image) {
        return service.imageScan(image);
    }
}
