package dev.nebulaops.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class RedisIdentityCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl = Duration.ofSeconds(45);

    public RedisIdentityCache(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public Optional<List<Object>> getList(String key) {
        try {
            String raw = redis.opsForValue().get(key);
            if (raw == null || raw.isBlank()) return Optional.empty();
            return Optional.of(mapper.readValue(raw, new TypeReference<List<Object>>() {}));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public void putList(String key, Object value) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
        }
    }

    public void evictRealm(String realm) {
        try {
            var keys = redis.keys("identity:" + realm + ":*");
            if (keys != null && !keys.isEmpty()) redis.delete(keys);
        } catch (Exception ignored) {
        }
    }

    public Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("realDataOnly", true);
        try {
            String key = "identity:cache:health";
            redis.opsForValue().set(key, "ok", Duration.ofSeconds(10));
            String value = redis.opsForValue().get(key);
            out.put("live", "ok".equals(value));
            out.put("state", "ok".equals(value) ? "REDIS_CACHE_AVAILABLE" : "REDIS_CACHE_WRITE_READ_MISMATCH");
            out.put("message", "ok".equals(value) ? "Redis identity cache is reachable." : "Redis responded but health value was not read back correctly.");
        } catch (Exception e) {
            out.put("live", false);
            out.put("state", "REDIS_CACHE_UNAVAILABLE");
            out.put("message", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return out;
    }
}
