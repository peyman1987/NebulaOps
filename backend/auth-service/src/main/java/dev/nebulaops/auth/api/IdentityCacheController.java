package dev.nebulaops.auth.api;

import dev.nebulaops.auth.service.RedisIdentityCache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/identity/cache")
public class IdentityCacheController {
    private final RedisIdentityCache cache;

    public IdentityCacheController(RedisIdentityCache cache) {
        this.cache = cache;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return cache.status();
    }
}
