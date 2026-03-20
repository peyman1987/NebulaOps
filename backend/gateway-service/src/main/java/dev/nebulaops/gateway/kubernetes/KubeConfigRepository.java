package dev.nebulaops.gateway.kubernetes;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface KubeConfigRepository extends MongoRepository<KubeConfigRecord, String> {
    Optional<KubeConfigRecord> findByName(String name);
}
