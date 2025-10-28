package dev.nebulaops.mvc.api;

import dev.nebulaops.mvc.config.AppProperties;
import dev.nebulaops.mvc.domain.WorkItem;
import dev.nebulaops.mvc.domain.WorkItemStatus;
import dev.nebulaops.mvc.service.WorkItemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/spring-mvc")
public class WorkItemApiController {
    private final WorkItemService service;
    private final AppProperties properties;

    public WorkItemApiController(WorkItemService service, AppProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "service", "spring-mvc-service",
                "displayName", properties.displayName(),
                "environment", properties.environment(),
                "gatewayUrl", properties.gatewayUrl(),
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/items")
    public List<WorkItem> list() {
        return service.findAll();
    }

    @GetMapping("/items/{id}")
    public WorkItem get(@PathVariable String id) {
        return service.findById(id);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkItem create(@Valid @RequestBody WorkItemRequest request) {
        return service.create(request.title(), request.description(), request.owner());
    }

    @PutMapping("/items/{id}")
    public WorkItem update(@PathVariable String id, @Valid @RequestBody WorkItemRequest request) {
        return service.update(id, request.title(), request.description(), request.owner());
    }

    @PatchMapping("/items/{id}/status/{status}")
    public WorkItem updateStatus(@PathVariable String id, @PathVariable WorkItemStatus status) {
        return service.changeStatus(id, status);
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    public record WorkItemRequest(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 1000) String description,
            @NotBlank @Size(max = 80) String owner
    ) {}
}
