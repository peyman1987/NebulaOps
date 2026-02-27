package dev.nebulaops.auth.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

/**
 * v22.5 — JWT token generation and validation (JJWT 0.12.x).
 * HS256-signed. Set JWT_SECRET env var in production (min 32 chars).
 * Access token: 24h. Refresh token: 7d.
 */
@Component
public class JwtService {

    private static final long ACCESS_TTL_MS  = 24L * 3600 * 1000;
    private static final long REFRESH_TTL_MS = 7L  * 24 * 3600 * 1000;

    private final SecretKey key;

    public JwtService(@Value("${jwt.secret:nebulaops-v22-5-dev-secret-key-min-32-chars!!}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String userId, String email,
                                      String displayName, Set<String> roles, String orgId) {
        return Jwts.builder()
                .subject(userId)
                .claim("email",       email)
                .claim("displayName", displayName)
                .claim("roles",       roles)
                .claim("orgId",       orgId)
                .claim("type",        "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TTL_MS))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_TTL_MS))
                .signWith(key)
                .compact();
    }

    public Claims validateAndParse(String token) {
        return Jwts.parser().verifyWith(key).build()
                   .parseSignedClaims(token).getPayload();
    }

    public boolean isValid(String token) {
        try { validateAndParse(token); return true; }
        catch (JwtException | IllegalArgumentException e) { return false; }
    }

    public String extractUserId(String token) {
        return validateAndParse(token).getSubject();
    }
}
