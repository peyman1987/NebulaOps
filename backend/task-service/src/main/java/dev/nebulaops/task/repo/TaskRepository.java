package dev.nebulaops.task.repo;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import dev.nebulaops.task.domain.TaskDocument;

public interface TaskRepository extends MongoRepository<TaskDocument, String> {
    List<TaskDocument> findByOrganizationId(String organizationId);
}
