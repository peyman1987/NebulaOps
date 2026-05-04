package dev.nebulaops.auth.api;

import dev.nebulaops.auth.domain.UserAccount;
import dev.nebulaops.auth.repo.UserAccountRepository;
import dev.nebulaops.auth.service.JwtService;
import io.jsonwebtoken.Claims;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * v23.3 — Auth REST API with real JWT tokens (JJWT 0.12).
 *
 * POST /api/auth/login    → { accessToken, refreshToken, tokenType, expiresIn, user }
 * POST /api/auth/register → { user }
 * POST /api/auth/refresh  → { accessToken, tokenType, expiresIn }
 * POST /api/auth/logout   → { ok }
 * GET  /api/auth/me       → { user }  (requires Authorization: Bearer <token>)
 * GET  /api/auth/healthz  → { status }
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAccountRepository users;
    private final JwtService            jwt;
    private final boolean localAdminEnabled;
    private final String localAdminEmail;
    private final String localAdminPassword;
    private final String defaultOrganizationId;

    public AuthController(UserAccountRepository users,
                          JwtService jwt,
                          @Value("${nebulaops.auth.local-admin.enabled:false}") boolean localAdminEnabled,
                          @Value("${nebulaops.auth.local-admin.email:}") String localAdminEmail,
                          @Value("${nebulaops.auth.local-admin.password:}") String localAdminPassword,
                          @Value("${nebulaops.default-organization-id:nebulaops}") String defaultOrganizationId) {
        this.users = users;
        this.jwt   = jwt;
        this.localAdminEnabled = localAdminEnabled;
        this.localAdminEmail = localAdminEmail == null ? "" : localAdminEmail.trim();
        this.localAdminPassword = localAdminPassword == null ? "" : localAdminPassword;
        this.defaultOrganizationId = defaultOrganizationId == null || defaultOrganizationId.isBlank() ? "nebulaops" : defaultOrganizationId.trim();
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (req.email() == null || req.password() == null)
            return ResponseEntity.badRequest().body(Map.of("error","email and password required"));
        if (users.findByEmail(req.email()).isPresent())
            return ResponseEntity.badRequest().body(Map.of("error","email already registered"));
        if (req.password().length() < 6)
            return ResponseEntity.badRequest().body(Map.of("error","password must be at least 6 characters"));

        String orgId = (req.organizationId() == null || req.organizationId().isBlank())
                       ? defaultOrganizationId : req.organizationId();
        // In production: BCrypt hash. Dev mode: plain prefix.
        String pwdStored = "plain-dev-only-" + req.password();
        var user = new UserAccount(null, req.email(),
                req.displayName() == null ? req.email() : req.displayName(),
                pwdStored, Set.of("USER"), orgId, Instant.now());
        var saved = users.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("user", toResponse(saved)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (req.email() == null || req.password() == null)
            return ResponseEntity.badRequest().body(Map.of("error","email and password required"));

        var userOpt = users.findByEmail(req.email());

        // Optional local admin shortcut is disabled by default and only works when
        // both email/password are supplied through external configuration. No static
        // account or bundled credential is accepted at runtime.
        if (userOpt.isEmpty() && localAdminEnabled && matchesConfiguredLocalAdmin(req)) {
            return ResponseEntity.ok(configuredLocalAdminTokenResponse(req.email()));
        }

        if (userOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                 .body(Map.of("error","Invalid credentials"));

        var user = userOpt.get();
        boolean pwdMatch = user.passwordHash().equals("plain-dev-only-" + req.password());
        if (!pwdMatch)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                 .body(Map.of("error","Invalid credentials"));

        String accessToken  = jwt.generateAccessToken(
                user.id(), user.email(), user.displayName(), user.roles(), user.organizationId());
        String refreshToken = jwt.generateRefreshToken(user.id());

        return ResponseEntity.ok(Map.of(
                "accessToken",  accessToken,
                "refreshToken", refreshToken,
                "tokenType",    "Bearer",
                "expiresIn",    86400,
                "user",         toResponse(user)
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestHeader("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        if (token == null || !jwt.isValid(token))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","Invalid refresh token"));

        String userId = jwt.extractUserId(token);
        var userOpt = users.findById(userId);
        if (userOpt.isEmpty())
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","User not found"));

        var user = userOpt.get();
        String newAccess = jwt.generateAccessToken(
                user.id(), user.email(), user.displayName(), user.roles(), user.organizationId());
        return ResponseEntity.ok(Map.of(
                "accessToken", newAccess,
                "tokenType",   "Bearer",
                "expiresIn",   86400
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value="Authorization",required=false) String authHeader) {
        String token = extractToken(authHeader);
        if (token == null || !jwt.isValid(token))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","Token required"));
        Claims claims = jwt.validateAndParse(token);
        return ResponseEntity.ok(Map.of("user", Map.of(
                "id",          claims.getSubject(),
                "email",       claims.get("email",String.class),
                "displayName", claims.get("displayName",String.class),
                "roles",       claims.get("roles"),
                "orgId",       claims.get("orgId",String.class)
        )));
    }

    @PostMapping("/logout")
    public Map<String, Object> logout() {
        // Stateless JWT — client must delete the token.
        // In production: add token to a Redis blacklist here.
        return Map.of("ok", true, "message", "Token invalidated client-side");
    }

    @GetMapping("/healthz")
    public Map<String, String> health() { return Map.of("status","AUTH_OK","version","23.3"); }

    // ── helpers ───────────────────────────────────────────────────────────────

    private boolean matchesConfiguredLocalAdmin(LoginRequest req) {
        return !localAdminEmail.isBlank()
                && !localAdminPassword.isBlank()
                && localAdminEmail.equals(req.email())
                && localAdminPassword.equals(req.password());
    }

    private Map<String, Object> configuredLocalAdminTokenResponse(String email) {
        // Return a normal NebulaOps signed access token for the externally configured
        // local admin. The package does not ship hard-coded fallback users.
        String accessToken = jwt.generateAccessToken(
                "configured-local-admin", email, "Configured Local Admin", Set.of("ADMIN", "USER"), defaultOrganizationId);
        String refreshToken = jwt.generateRefreshToken("configured-local-admin");
        return Map.of(
                "accessToken",  accessToken,
                "refreshToken", refreshToken,
                "tokenType",    "Bearer",
                "expiresIn",    86400,
                "user", Map.of("id","configured-local-admin","email",email,
                               "displayName","Configured Local Admin","roles",Set.of("ADMIN","USER"),
                               "organizationId", defaultOrganizationId)
        );
    }

    private String extractToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.substring(7).trim();
    }

    private UserResponse toResponse(UserAccount u) {
        return new UserResponse(u.id(), u.email(), u.displayName(), u.roles(), u.organizationId());
    }

    public record RegisterRequest(String email, String displayName, String password, String organizationId) {}
    public record LoginRequest(String email, String password) {}
    public record UserResponse(String id, String email, String displayName, Set<String> roles, String organizationId) {}
}
