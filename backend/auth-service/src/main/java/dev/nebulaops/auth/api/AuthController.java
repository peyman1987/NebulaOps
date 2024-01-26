package dev.nebulaops.auth.api;

import dev.nebulaops.auth.domain.UserAccount;
import dev.nebulaops.auth.repo.UserAccountRepository;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserAccountRepository users;

    public AuthController(UserAccountRepository users) {
        this.users = users;
    }

    private static UserResponse toResponse(UserAccount user) {
        return new UserResponse(user.id(), user.email(), user.displayName(), user.roles(), user.organizationId());
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (req.email() == null || req.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "email and password are required"));
        }
        if (users.findByEmail(req.email()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email already exists"));
        }

        var organizationId = req.organizationId() == null || req.organizationId().isBlank() ? "demo-org" : req.organizationId();
        var user = new UserAccount(
                null,
                req.email(),
                req.displayName() == null ? req.email() : req.displayName(),
                "demo-hash-" + req.password(),
                Set.of("USER"),
                organizationId,
                Instant.now());

        var saved = users.save(user);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        var user = users.findByEmail(req.email()).orElseThrow();
        return Map.of(
                "accessToken", "demo-jwt-" + UUID.randomUUID(),
                "tokenType", "Bearer",
                "user", toResponse(user));
    }

    @GetMapping("/healthz")
    public Map<String, String> health() {
        return Map.of("status", "AUTH_OK");
    }

    public record RegisterRequest(String email, String displayName, String password, String organizationId) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record UserResponse(String id, String email, String displayName, Set<String> roles, String organizationId) {
    }
}
