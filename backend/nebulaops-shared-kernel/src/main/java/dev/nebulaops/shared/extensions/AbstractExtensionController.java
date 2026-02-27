package dev.nebulaops.shared.extensions;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public abstract class AbstractExtensionController {
    protected abstract String id();
    protected abstract String name();
    protected abstract String version();
    protected List<ExtensionCapability> extensionCapabilities() { return List.of(); }
    protected boolean live() { return true; }

    public Map<String, Object> healthz() {
        return Map.of("status", live() ? "UP" : "DEGRADED", "id", id(), "name", name(), "version", version(), "generatedAt", Instant.now().toString());
    }

    public Map<String, Object> readyz() {
        return Map.of("ready", live(), "id", id(), "name", name(), "version", version(), "generatedAt", Instant.now().toString());
    }

    public ExtensionRuntimeStatus runtimeStatus() {
        return new ExtensionRuntimeStatus(id(), name(), live() ? "CONNECTED" : "DEGRADED", version(), live(), extensionCapabilities(), Instant.now());
    }
}
