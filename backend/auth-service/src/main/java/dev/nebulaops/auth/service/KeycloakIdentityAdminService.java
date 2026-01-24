package dev.nebulaops.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;

@Service
public class KeycloakIdentityAdminService {
    private final RestTemplate rest = new RestTemplate();
    private final RedisIdentityCache cache;
    private final String baseUrl;
    private final String adminClientId;
    private final String adminUsername;
    private final String adminPassword;
    private final AuditClient auditClient;

    public KeycloakIdentityAdminService(
            RedisIdentityCache cache,
            @Value("${keycloak.admin.base-url:http://localhost:8180}") String baseUrl,
            @Value("${keycloak.admin.client-id:admin-cli}") String adminClientId,
            @Value("${keycloak.admin.username:admin}") String adminUsername,
            @Value("${keycloak.admin.password:admin}") String adminPassword,
            AuditClient auditClient) {
        this.cache = cache;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.adminClientId = adminClientId;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.auditClient = auditClient;
    }

    public List<Object> users(String realm, String search) {
        String cacheKey = "identity:" + realm + ":users:" + (search == null ? "" : search.trim().toLowerCase(Locale.ROOT));
        var cached = cache.getList(cacheKey);
        if (cached.isPresent()) return cached.get();
        URI uri = UriComponentsBuilder.fromHttpUrl(adminUrl(realm, "/users"))
                .queryParamIfPresent("search", Optional.ofNullable(blankToNull(search)))
                .queryParam("max", 250)
                .build()
                .toUri();
        List<Object> result = getList(uri);
        cache.putList(cacheKey, result);
        return result;
    }

    public List<Object> groups(String realm) {
        return cachedList(realm, "groups", adminUrl(realm, "/groups?max=250"));
    }

    public List<Object> roles(String realm) {
        return cachedList(realm, "roles", adminUrl(realm, "/roles"));
    }

    public Map<String, Object> createUser(String realm, Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>(body == null ? Map.of() : body);
        payload.putIfAbsent("enabled", true);
        ResponseEntity<String> response = exchange(adminUrl(realm, "/users"), HttpMethod.POST, payload, String.class);
        cache.evictRealm(realm);
        auditClient.identityEvent(realm, "create", "user", response.getHeaders().getFirst(HttpHeaders.LOCATION), payload);
        return Map.of("created", response.getStatusCode().is2xxSuccessful(), "location", response.getHeaders().getFirst(HttpHeaders.LOCATION));
    }

    public Map<String, Object> updateUser(String realm, String id, Map<String, Object> body) {
        exchange(adminUrl(realm, "/users/" + safe(id)), HttpMethod.PUT, body == null ? Map.of() : body, String.class);
        cache.evictRealm(realm);
        auditClient.identityEvent(realm, "update", "user", id, body == null ? Map.of() : body);
        return Map.of("updated", true, "id", id);
    }

    public Map<String, Object> disableUser(String realm, String id) {
        Map<String, Object> current = getMap(adminUrl(realm, "/users/" + safe(id)));
        current.put("enabled", false);
        exchange(adminUrl(realm, "/users/" + safe(id)), HttpMethod.PUT, current, String.class);
        cache.evictRealm(realm);
        auditClient.identityEvent(realm, "disable", "user", id, current);
        return Map.of("disabled", true, "id", id);
    }

    public Map<String, Object> createGroup(String realm, Map<String, Object> body) {
        exchange(adminUrl(realm, "/groups"), HttpMethod.POST, body == null ? Map.of() : body, String.class);
        cache.evictRealm(realm);
        auditClient.identityEvent(realm, "create", "group", String.valueOf(body == null ? "" : body.getOrDefault("name", "")), body == null ? Map.of() : body);
        return Map.of("created", true, "name", String.valueOf(body == null ? "" : body.getOrDefault("name", "")));
    }

    public Map<String, Object> updateGroup(String realm, String id, Map<String, Object> body) {
        exchange(adminUrl(realm, "/groups/" + safe(id)), HttpMethod.PUT, body == null ? Map.of() : body, String.class);
        cache.evictRealm(realm);
        auditClient.identityEvent(realm, "update", "group", id, body == null ? Map.of() : body);
        return Map.of("updated", true, "id", id);
    }

    public Map<String, Object> disableGroup(String realm, String id) {
        Map<String, Object> current = getMap(adminUrl(realm, "/groups/" + safe(id)));
        Map<String, Object> attributes = new LinkedHashMap<>();
        Object existing = current.get("attributes");
        if (existing instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) attributes.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        attributes.put("disabled", List.of("true"));
        current.put("attributes", attributes);
        exchange(adminUrl(realm, "/groups/" + safe(id)), HttpMethod.PUT, current, String.class);
        cache.evictRealm(realm);
        auditClient.identityEvent(realm, "disable", "group", id, current);
        return Map.of("disabled", true, "id", id);
    }

    public Map<String, Object> createRole(String realm, Map<String, Object> body) {
        exchange(adminUrl(realm, "/roles"), HttpMethod.POST, body == null ? Map.of() : body, String.class);
        cache.evictRealm(realm);
        auditClient.identityEvent(realm, "create", "role", String.valueOf(body == null ? "" : body.getOrDefault("name", "")), body == null ? Map.of() : body);
        return Map.of("created", true, "name", String.valueOf(body == null ? "" : body.getOrDefault("name", "")));
    }

    public Map<String, Object> updateRole(String realm, String name, Map<String, Object> body) {
        exchange(adminUrl(realm, "/roles/" + safe(name)), HttpMethod.PUT, body == null ? Map.of() : body, String.class);
        cache.evictRealm(realm);
        auditClient.identityEvent(realm, "update", "role", name, body == null ? Map.of() : body);
        return Map.of("updated", true, "name", name);
    }

    public Map<String, Object> disableRole(String realm, String name) {
        Map<String, Object> current = getMap(adminUrl(realm, "/roles/" + safe(name)));
        Map<String, Object> attributes = new LinkedHashMap<>();
        Object existing = current.get("attributes");
        if (existing instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) attributes.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        attributes.put("disabled", List.of("true"));
        current.put("attributes", attributes);
        exchange(adminUrl(realm, "/roles/" + safe(name)), HttpMethod.PUT, current, String.class);
        cache.evictRealm(realm);
        auditClient.identityEvent(realm, "disable", "role", name, current);
        return Map.of("disabled", true, "name", name);
    }

    private List<Object> cachedList(String realm, String resource, String url) {
        String cacheKey = "identity:" + realm + ":" + resource;
        var cached = cache.getList(cacheKey);
        if (cached.isPresent()) return cached.get();
        List<Object> result = getList(URI.create(url));
        cache.putList(cacheKey, result);
        return result;
    }

    private List<Object> getList(URI uri) {
        ResponseEntity<List<Object>> response = rest.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers()), new ParameterizedTypeReference<>() {});
        return response.getBody() == null ? List.of() : response.getBody();
    }

    private Map<String, Object> getMap(String url) {
        ResponseEntity<Map<String, Object>> response = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers()), new ParameterizedTypeReference<>() {});
        return response.getBody() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(response.getBody());
    }

    private <T> ResponseEntity<T> exchange(String url, HttpMethod method, Object body, Class<T> type) {
        HttpHeaders headers = headers();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url, method, new HttpEntity<>(body, headers), type);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private String accessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", adminClientId);
        form.add("username", adminUsername);
        form.add("password", adminPassword);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                baseUrl + "/realms/master/protocol/openid-connect/token",
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                new ParameterizedTypeReference<>() {});
        Object token = response.getBody() == null ? null : response.getBody().get("access_token");
        if (token == null) throw new IllegalStateException("Keycloak admin token unavailable");
        return String.valueOf(token);
    }

    private String adminUrl(String realm, String path) {
        return baseUrl + "/admin/realms/" + safe(realm) + path;
    }

    private String safe(String input) {
        return input == null ? "" : input.replaceAll("[^A-Za-z0-9_.:@-]", "");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
