package dev.nebulaops.mvc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nebulaops.mvc")
public record AppProperties(
        String displayName,
        String environment,
        String gatewayUrl
) {
    public AppProperties {
        if (displayName == null || displayName.isBlank()) {
            displayName = "NebulaOps Spring MVC Service";
        }
        if (environment == null || environment.isBlank()) {
            environment = "local";
        }
        if (gatewayUrl == null || gatewayUrl.isBlank()) {
            gatewayUrl = "http://localhost:8080";
        }
    }
}
