package dev.nebulaops.mvc.service;

import dev.nebulaops.mvc.domain.WorkItem;
import dev.nebulaops.mvc.domain.WorkItemStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class WorkItemService {
    private final ConcurrentMap<String, WorkItem> items = new ConcurrentHashMap<>();

    public WorkItemService() {
        // v23.3 live-only mode: no seeded/demo work items are created at startup.
        // Items are created only through the REST API during the current runtime.
    }

    public List<WorkItem> findAll() {
        return items.values().stream()
                .sorted(Comparator.comparing(WorkItem::createdAt).reversed())
                .toList();
    }

    public WorkItem findById(String id) {
        WorkItem item = items.get(id);
        if (item == null) {
            throw new NoSuchElementException("Work item not found: " + id);
        }
        return item;
    }

    public WorkItem create(String title, String description, String owner) {
        WorkItem item = WorkItem.create(title, description == null ? "" : description, owner);
        items.put(item.id(), item);
        return item;
    }

    public WorkItem update(String id, String title, String description, String owner) {
        WorkItem current = findById(id);
        WorkItem updated = current.withContent(title, description == null ? "" : description, owner);
        items.put(id, updated);
        return updated;
    }

    public WorkItem changeStatus(String id, WorkItemStatus status) {
        WorkItem updated = findById(id).withStatus(status);
        items.put(id, updated);
        return updated;
    }

    public void delete(String id) {
        if (items.remove(id) == null) {
            throw new NoSuchElementException("Work item not found: " + id);
        }
    }
}
