package dev.nebulaops.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * v21.2 — RestTemplate config. Uses JdkClientHttpRequestFactory which supports
 * the full HTTP method set including PATCH (required for /api/tasks/{id}/status/{status}).
 */
@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate(new JdkClientHttpRequestFactory());
    }
}
