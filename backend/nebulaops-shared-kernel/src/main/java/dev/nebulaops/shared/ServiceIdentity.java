package dev.nebulaops.shared;

public record ServiceIdentity(String name, String version, String domain, int port) {}
