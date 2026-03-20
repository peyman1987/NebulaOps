package dev.nebulaops.gateway.kubernetes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class KubeConfigRegistryService {
    private final KubeConfigRepository repository;
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public KubeConfigRegistryService(KubeConfigRepository repository) {
        this.repository = repository;
    }

    public List<Map<String, Object>> listSummaries() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "updatedAt"))
                .stream()
                .map(this::summary)
                .toList();
    }

    public KubeConfigRecord save(Map<String, Object> request) {
        String kubeconfig = string(request.get("kubeconfig"));
        String name = string(request.get("name"));
        if (kubeconfig.isBlank()) throw new IllegalArgumentException("kubeconfig is required");

        Map<String, Object> parsed = parse(kubeconfig);
        String contextName = string(parsed.get("current-context"));
        Map<String, Object> selectedContext = selectedNamedEntry((List) parsed.get("contexts"), contextName);
        Map<String, Object> context = asMap(selectedContext.get("context"));
        String clusterName = string(context.get("cluster"));
        String namespace = string(context.getOrDefault("namespace", request.getOrDefault("namespace", "default")));
        Map<String, Object> selectedCluster = selectedNamedEntry((List) parsed.get("clusters"), clusterName);
        Map<String, Object> cluster = asMap(selectedCluster.get("cluster"));
        String server = string(cluster.get("server"));

        if (name.isBlank()) name = !contextName.isBlank() ? contextName : !clusterName.isBlank() ? clusterName : "kubeconfig-" + Instant.now().toEpochMilli();

        KubeConfigRecord record = repository.findByName(name).orElseGet(KubeConfigRecord::new);
        Instant now = Instant.now();
        if (record.getCreatedAt() == null) record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setName(name);
        record.setDescription(string(request.get("description")));
        record.setContextName(contextName);
        record.setClusterName(clusterName);
        record.setNamespace(namespace.isBlank() ? "default" : namespace);
        record.setServer(server);
        record.setKubeconfig(kubeconfig);
        return repository.save(record);
    }

    public Optional<KubeConfigRecord> find(String id) {
        if (id == null || id.isBlank() || "current".equals(id) || "current-context".equals(id)) return Optional.empty();
        return repository.findById(id).or(() -> repository.findByName(id));
    }

    public void delete(String id) {
        find(id).ifPresent(repository::delete);
    }

    public Path writeTempKubeconfig(String id) throws IOException {
        KubeConfigRecord record = find(id).orElseThrow(() -> new IllegalArgumentException("kubeconfig not found: " + id));
        Path tmp = Files.createTempFile("nebulaops-kubeconfig-", ".yaml");
        Files.writeString(tmp, record.getKubeconfig());
        return tmp;
    }

    public Map<String, Object> summary(KubeConfigRecord record) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", record.getId());
        out.put("name", record.getName());
        out.put("description", record.getDescription());
        out.put("contextName", record.getContextName());
        out.put("clusterName", record.getClusterName());
        out.put("server", maskServer(record.getServer()));
        out.put("namespace", record.getNamespace());
        out.put("createdAt", record.getCreatedAt());
        out.put("updatedAt", record.getUpdatedAt());
        out.put("source", "mongodb:kubernetes_kubeconfigs");
        return out;
    }

    private Map<String, Object> parse(String kubeconfig) {
        try {
            return yamlMapper.readValue(kubeconfig, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid kubeconfig YAML: " + e.getMessage());
        }
    }

    private Map<String, Object> selectedNamedEntry(List entries, String selectedName) {
        if (entries == null || entries.isEmpty()) return Collections.emptyMap();
        for (Object item : entries) {
            Map<String, Object> entry = asMap(item);
            if (Objects.equals(string(entry.get("name")), selectedName)) return entry;
        }
        return asMap(entries.get(0));
    }

    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String maskServer(String server) {
        if (server == null || server.isBlank()) return "";
        return server.replaceAll("(?i)(https?://)([^/@:]+)(:[0-9]+)?", "$1$2$3");
    }
}
