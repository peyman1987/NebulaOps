package dev.nebulaops.shared.extensions;

import java.time.Instant;
import java.util.List;

public record ExtensionRuntimeStatus(String id, String name, String state, String version, boolean live, List<ExtensionCapability> capabilities, Instant generatedAt) {}
