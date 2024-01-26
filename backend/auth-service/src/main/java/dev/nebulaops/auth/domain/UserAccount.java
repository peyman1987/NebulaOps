package dev.nebulaops.auth.domain;

import java.time.Instant;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("users")
public record UserAccount(@Id String id, @Indexed(unique = true) String email, String displayName, String passwordHash,
                          Set<String> roles, String organizationId, Instant createdAt) {
}
