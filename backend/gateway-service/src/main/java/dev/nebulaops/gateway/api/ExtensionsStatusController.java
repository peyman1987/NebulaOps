package dev.nebulaops.gateway.api;

import dev.nebulaops.gateway.service.DockerRuntimeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v23.3 — installed extension discovery.
 *
 * The endpoint reports extensions from the live Docker runtime by inspecting
 * running/stopped NebulaOps MFE containers. If Docker is unavailable it returns
 * an explicit runtime state and does not fabricate extension records.
 */
@RestController
@RequestMapping("/api/extensions")
public class ExtensionsStatusController {
    private final DockerRuntimeService docker;

    public ExtensionsStatusController(DockerRuntimeService docker) {
        this.docker = docker;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> containers = docker.containers();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "docker-runtime");
        out.put("executedAt", Instant.now().toString());
        out.put("realDataOnly", true);
        out.put("live", Boolean.TRUE.equals(containers.get("live")));
        out.put("toolStatus", containers.get("toolStatus"));

        List<Map<String, Object>> items = new ArrayList<>();
        Object raw = containers.get("items");
        if (Boolean.TRUE.equals(containers.get("live")) && raw instanceof List<?> list) {
            for (Object value : list) {
                if (!(value instanceof Map<?, ?> map)) continue;
                String name = firstName(map.get("Names"));
                String image = String.valueOf(map.get("Image") == null ? "" : map.get("Image"));
                String labels = String.valueOf(map.get("Labels") == null ? "" : map.get("Labels"));
                String probe = (name + " " + image + " " + labels).toLowerCase(Locale.ROOT);
                if (!probe.contains("mfe-") && !probe.contains("nebulaops-mfe")) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", shortId(String.valueOf(map.get("Id") == null ? "" : map.get("Id"))));
                row.put("name", name);
                row.put("image", image);
                row.put("status", map.get("Status"));
                row.put("state", map.get("State"));
                row.put("ports", map.get("Ports"));
                row.put("created", map.get("Created"));
                row.put("live", "running".equalsIgnoreCase(String.valueOf(map.get("State") == null ? "" : map.get("State"))));
                items.add(row);
            }
        }

        out.put("items", items);
        out.put("count", items.size());
        if (!Boolean.TRUE.equals(out.get("live"))) {
            out.put("state", "EXTENSION_DISCOVERY_DOCKER_UNAVAILABLE");
            out.put("message", "Installed extension discovery requires the Docker socket because extension status is derived from live MFE containers.");
        } else if (items.isEmpty()) {
            out.put("state", "NO_EXTENSION_CONTAINERS_FOUND");
            out.put("message", "Docker is reachable but no NebulaOps MFE extension containers were returned by Docker Engine.");
        }
        return out;
    }

    private String firstName(Object names) {
        if (names instanceof List<?> list && !list.isEmpty()) return String.valueOf(list.get(0)).replaceFirst("^/+", "");
        return "";
    }

    private String shortId(String id) {
        return id.length() > 12 ? id.substring(0, 12) : id;
    }
}
