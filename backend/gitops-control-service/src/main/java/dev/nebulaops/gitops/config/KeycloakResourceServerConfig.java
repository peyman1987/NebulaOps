package dev.nebulaops.gitops.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * v24.1 — Keycloak resource-server integration.
 *
 * When nebulaops.security.enabled=true, the service validates Bearer JWTs
 * issued by the NebulaOps Keycloak realm. Public endpoints are limited to
 * health, metrics/OpenAPI and the legacy bootstrap auth routes.
 *
 * Realm roles are converted to Spring authorities:
 *   nebula-admin    -> ROLE_NEBULA_ADMIN
 *   nebula-operator -> ROLE_NEBULA_OPERATOR
 *   nebula-viewer   -> ROLE_NEBULA_VIEWER
 */
@Configuration
@EnableMethodSecurity
public class KeycloakResourceServerConfig {

    /**
     * v24.1 local/Keycloak JWT bridge.
     *
     * Standalone micro frontends call /api/auth/login through their own Nginx
     * origin and receive the NebulaOps local HS256 JWT issued by auth-service.
     * Browser-shell / SSO flows may still pass Keycloak RS256 tokens.
     *
     * The decoder accepts both formats: local HS256 first, then Keycloak JWKS.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${jwt.secret:nebulaops-v24-1-dev-secret-key-min-32-chars!!}") String jwtSecret,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri) {

        SecretKey localKey = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtDecoder localDecoder = NimbusJwtDecoder.withSecretKey(localKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtDecoder keycloakDecoder = (jwkSetUri == null || jwkSetUri.isBlank())
                ? null
                : NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        return token -> {
            try {
                return localDecoder.decode(token);
            } catch (JwtException localFailure) {
                if (keycloakDecoder == null) {
                    throw localFailure;
                }
                return keycloakDecoder.decode(token);
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            @Value("${nebulaops.security.enabled:false}") boolean keycloakEnabled) throws Exception {

        http.csrf(csrf -> csrf.disable());
        http.cors(Customizer.withDefaults());

        if (!keycloakEnabled) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/",
                        "/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/refresh",
                        "/api/auth/healthz"
                ).permitAll()
                .anyRequest().authenticated()
        ).oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        scopes.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            Collection<GrantedAuthority> scopeAuthorities = scopes.convert(jwt);
            if (scopeAuthorities != null) {
                authorities.addAll(scopeAuthorities);
            }

            Object localRolesObj = jwt.getClaim("roles");
            if (localRolesObj instanceof Collection<?> localRoles) {
                for (Object role : localRoles) {
                    addRole(authorities, role);
                }
            }

            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                Object rolesObj = realmAccess.get("roles");
                if (rolesObj instanceof Collection<?> roles) {
                    for (Object role : roles) {
                        addRole(authorities, role);
                    }
                }
            }
            return authorities;
        });
        return converter;
    }

    private static void addRole(Collection<GrantedAuthority> authorities, Object role) {
        if (role == null) {
            return;
        }
        String normalized = role.toString()
                .trim()
                .replace('-', '_')
                .replace('.', '_')
                .toUpperCase();
        if (!normalized.isBlank()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + normalized));
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With", "X-NebulaOps-Auth-Bridge"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
