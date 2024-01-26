package dev.nebulaops.file.repo;

import org.springframework.data.mongodb.repository.MongoRepository;
import dev.nebulaops.file.domain.FileMetadata;

public interface FileMetadataRepository extends MongoRepository<FileMetadata, String> {
}
