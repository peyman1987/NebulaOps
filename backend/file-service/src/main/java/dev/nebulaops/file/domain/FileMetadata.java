package dev.nebulaops.file.domain;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("files")
public record FileMetadata(@Id String id, String organizationId, String taskId, String fileName, String contentType,
                           long size, String objectKey, Instant uploadedAt) {
}
