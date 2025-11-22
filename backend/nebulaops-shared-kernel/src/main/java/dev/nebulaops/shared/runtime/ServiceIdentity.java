package dev.nebulaops.shared.runtime;

public record ServiceIdentity(String serviceName, String version, int port) {
    public static ServiceIdentity of(String serviceName, String version, int port) {
        return new ServiceIdentity(serviceName, version, port);
    }
}
