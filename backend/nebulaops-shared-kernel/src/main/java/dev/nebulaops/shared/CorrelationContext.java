package dev.nebulaops.shared;

import java.util.UUID;

public final class CorrelationContext {
    private CorrelationContext() {}
    public static String newId() {
        return "corr-" + UUID.randomUUID();
    }
}
