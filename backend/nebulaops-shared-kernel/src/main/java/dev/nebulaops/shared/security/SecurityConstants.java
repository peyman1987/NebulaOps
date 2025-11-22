package dev.nebulaops.shared.security;

public final class SecurityConstants {
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String ROLE_PREFIX = "ROLE_";
    public static final String DEFAULT_REALM = "nebulaops";

    private SecurityConstants() {
    }

    public static boolean isBearerToken(String value) {
        return value != null && value.startsWith(BEARER_PREFIX) && value.length() > BEARER_PREFIX.length();
    }
}
