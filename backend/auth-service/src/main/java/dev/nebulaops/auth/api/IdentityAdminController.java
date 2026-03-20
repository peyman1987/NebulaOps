package dev.nebulaops.auth.api;

import dev.nebulaops.auth.service.KeycloakIdentityAdminService;
import dev.nebulaops.auth.service.RedisIdentityCache;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/identity/realms/{realm}")
public class IdentityAdminController {
    private final KeycloakIdentityAdminService identity;
    private final RedisIdentityCache cache;

    public IdentityAdminController(KeycloakIdentityAdminService identity, RedisIdentityCache cache) {
        this.identity = identity;
        this.cache = cache;
    }

    @GetMapping("/status")
    public Map<String, Object> realmStatus(@PathVariable String realm) {
        return identity.realmStatus(realm);
    }

    @GetMapping("/cache/status")
    public Map<String, Object> realmCacheStatus(@PathVariable String realm) {
        Map<String, Object> out = new java.util.LinkedHashMap<>(cache.status());
        out.put("realm", realm);
        return out;
    }

    @GetMapping("/users")
    public List<Object> users(@PathVariable String realm, @RequestParam(required = false) String search) {
        return identity.users(realm, search);
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(@PathVariable String realm, @RequestBody Map<String, Object> body) {
        return identity.createUser(realm, body);
    }

    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable String realm, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return identity.updateUser(realm, id, body);
    }

    @PatchMapping("/users/{id}/disable")
    public Map<String, Object> disableUser(@PathVariable String realm, @PathVariable String id) {
        return identity.disableUser(realm, id);
    }

    @GetMapping("/groups")
    public List<Object> groups(@PathVariable String realm) {
        return identity.groups(realm);
    }

    @PostMapping("/groups")
    public Map<String, Object> createGroup(@PathVariable String realm, @RequestBody Map<String, Object> body) {
        return identity.createGroup(realm, body);
    }

    @PutMapping("/groups/{id}")
    public Map<String, Object> updateGroup(@PathVariable String realm, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return identity.updateGroup(realm, id, body);
    }

    @PatchMapping("/groups/{id}/disable")
    public Map<String, Object> disableGroup(@PathVariable String realm, @PathVariable String id) {
        return identity.disableGroup(realm, id);
    }

    @GetMapping("/roles")
    public List<Object> roles(@PathVariable String realm) {
        return identity.roles(realm);
    }

    @PostMapping("/roles")
    public Map<String, Object> createRole(@PathVariable String realm, @RequestBody Map<String, Object> body) {
        return identity.createRole(realm, body);
    }

    @PutMapping("/roles/{name}")
    public Map<String, Object> updateRole(@PathVariable String realm, @PathVariable String name, @RequestBody Map<String, Object> body) {
        return identity.updateRole(realm, name, body);
    }

    @PatchMapping("/roles/{name}/disable")
    public Map<String, Object> disableRole(@PathVariable String realm, @PathVariable String name) {
        return identity.disableRole(realm, name);
    }
}
