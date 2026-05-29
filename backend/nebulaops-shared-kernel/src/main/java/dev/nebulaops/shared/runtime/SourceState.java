package dev.nebulaops.shared.runtime;

/**
 * Standard live-runtime source state used by v24.1 gateway and remotes.
 * Integrations must report their real state; unavailable dependencies are
 * represented explicitly instead of falling back to generated operational data.
 */
public enum SourceState {
    READY,
    DEGRADED,
    UNAVAILABLE,
    NOT_CONFIGURED,
    ERROR,
    LOADING
}
