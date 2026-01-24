package dev.nebulaops.auth.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

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
}
